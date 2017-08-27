const BINARY = true;
const REMOVE_LOW_SEVERITY = false;

// Takes a set of points and a set of street geometries. Fits labels to those streets, giving counts of how many labels
// of each label type are closest to each street. Streets are then also split up into smaller line segments, and the
// same counts are then tabulated for each of those segments.
function setupIRR(data) {
    // unpack different pieces of data
    let streetsData = data.streets;
    let labelsData = data.labels;
    let hits = [...new Set(labelsData.features.map(label => label.properties.hit_id))]; // gets unique set of hits
    let turkers = [...new Set(labelsData.features.map(label => label.properties.turker_id))]; // gets unique set of turkers
    let output = [];
    for(let i = 0; i < hits.length; i++) output[i] = {};

    // print out label counts by type
    let labelCounts = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0};
    for (let i = 0; i < labelsData.features.length; i++) {
        labelCounts[labelsData.features[i].properties.label_type] += 1;
    }
    console.log(labelCounts);

    for(let hitIndex = 0; hitIndex < hits.length; hitIndex++) {
        let currHit = hits[hitIndex];
        let routes = [...new Set(labelsData.features.filter(label => label.properties.hit_id === currHit).map(label => label.properties.route_id))];
        let labs = labelsData.features.filter(label => label.properties.hit_id === currHit);

        // TODO check that the extra streets that have been removed do not actually have labels by them
        // TODO make it so that it isn't assumed that the routes streets are in order
        let streets = streetsData.features.filter(street => routes.indexOf(street.properties.route_id) >= 0);
        // check that each mission has the right distance

        for (let routeIndex = 0; routeIndex < routes.length; routeIndex++) {
            let thisRouteStreets = streets.filter(street => street.properties.route_id === routes[routeIndex]);
            let routeDist = 0;
            if (routeIndex < 2) {
                routeDist = 0.3048; // 1000ft in km, first 2 missions/routes
            }
            else {
                routeDist = 0.6096; // 2000ft in km, possible third mission/route
            }

            let distAcc = 0;
            for (let streetIndex = 0; streetIndex < thisRouteStreets.length; streetIndex++) {
                if (distAcc < routeDist) {
                    distAcc += turf.lineDistance(thisRouteStreets[streetIndex]);
                    // if this is the last route in the HIT and the last street in the route, remove the extra bit at
                    // the end of the street that wouldn't be audited
                    if (distAcc > routeDist) {
                        let s = thisRouteStreets[streetIndex];
                        let d = routeDist - (distAcc - turf.lineDistance(thisRouteStreets[streetIndex]));
                        s = turf.lineSliceAlong(s, 0, d);
                        let idx = streets.findIndex(street =>
                            street.properties.route_id === routes[routeIndex] &&
                            street.properties.street_edge_id === thisRouteStreets[streetIndex].properties.street_edge_id);
                        streets[idx] = s;
                    }
                }
                else {
                    // remove the extra streets associated with a route
                    streets = streets.filter(street =>
                        !(street.properties.route_id === routes[routeIndex] &&
                          street.properties.street_edge_id === thisRouteStreets[streetIndex].properties.street_edge_id));
                }
            }
        }

        // street level
        // initialize the street output object with 0's
        let streetOutput =
            {"CurbRamp": {}, "NoCurbRamp": {}, "NoSidewalk": {},"Obstacle": {}, "Occlusion": {}, "SurfaceProblem": {}};
        for (let label_type in streetOutput) {
            if (streetOutput.hasOwnProperty(label_type)) {
                streetOutput[label_type] = [];
                for (let i = 0; i < streets.length; i++) {
                    streetOutput[label_type][i] = {};
                    for (let j = 0; j < turkers.length; j++) {
                        streetOutput[label_type][i][turkers[j]] = 0;
                    }
                }
            }
        }

        // for each cluster, find the label nearest the center of the cluster, find which segment this label is
        // nearest, and mark all labels from that cluster as being in that segment
        let clusterIds = [...new Set(labs.map(label => label.properties.cluster_id))];
        for (let clustIndex = 0; clustIndex < clusterIds.length; clustIndex++) {
            let currLabels = labs.filter(label => label.properties.cluster_id === clusterIds[clustIndex]);
            if (REMOVE_LOW_SEVERITY && ["Obstacle", "SurfaceProblem"].indexOf(currLabels[0].properties.label_type) > 0) {
                currLabels = currLabels.filter(label => label.properties.temporary !== false);
            }
            if (currLabels.length > 0) {
                let centerPoint = turf.centerOfMass({"features": currLabels, "type": "FeatureCollection"});
                let repLabel = turf.nearest(centerPoint, {"features": currLabels, "type": "FeatureCollection"});
                let currType = repLabel.properties.label_type;

                // trying to exclude low severity surface problems and obstacles
                // if (["SurfaceProblem", "Obstacle"].indexOf(currLabel.properties.label_type) >= 0 && currLabel.properties.severity > 3) {
                // get closest segment to this label
                // http://turfjs.org/docs/#pointonline
                let streetIndex;
                let minDist = Number.POSITIVE_INFINITY;
                for (let i = 0; i < streets.length; i++) {
                    let closestPoint = turf.pointOnLine(streets[i], repLabel);
                    if (closestPoint.properties.dist < minDist) {
                        streetIndex = i;
                        minDist = closestPoint.properties.dist;
                    }
                }

                // increment this segment's count of labels (of this label type), distributing labels based on turker_id
                for (let turkerIndex = 0; turkerIndex < turkers.length; turkerIndex++) {
                    let turkerId = turkers[turkerIndex];
                    let labelCount = currLabels.filter(label => label.properties.turker_id === turkerId).length;
                    if (BINARY) {
                        let curr = streetOutput[currType][streetIndex][turkerId];
                        streetOutput[currType][streetIndex][turkerId] = Math.max(curr, Math.min(labelCount, 1));
                    } else {
                        streetOutput[currType][streetIndex][turkerId] += labelCount;
                    }
                }
            }
        }
        output[hitIndex].street = streetOutput;


        // segment level
        // combine streets into a set of contiguous linestrings
        // http://turfjs.org/docs/#combine -- combines the different streets into a single MultiLineString
        // http://turfjs.org/docs/#lineintersect -- lets you know the points where two lines intersect
        let combinedStreets = turf.combine({"features": streets, "type": "FeatureCollection"});


        let segDists = [0.005, 0.01]; // in meters
        for(let segDistIndex = 0; segDistIndex < segDists.length; segDistIndex++) {
            let segDist = segDists[segDistIndex];

            // Split streets into a bunch of little segments based on segDist and length of each contiguous segment. For
            // each contiguous set of streets, split the contiguous line up into equally sized segments, as close as
            // possible to segDist.
            // http://turfjs.org/docs/#linechunk
            let chunks = [];
            let contiguousStart = 0;
            let lineIndex = 0;
            while (lineIndex < streets.length) {
                contiguousStart = lineIndex;
                // search for end of contiguous segment
                while (lineIndex + 1 < streets.length && turf.lineIntersect(streets[lineIndex], streets[lineIndex+1]).features.length > 0) {
                    lineIndex++;
                }
                lineIndex++;

                // combine the streets, split the contiguous line up into equally sized segs, approx equal to segDist
                let contiguousStreets = turf.combine({"features": streets.slice(contiguousStart, lineIndex), "type": "FeatureCollection"});
                let nSegs = Math.round(turf.lineDistance(contiguousStreets) / segDist);
                let exactSegDist = turf.lineDistance(contiguousStreets) / nSegs;
                chunks = chunks.concat(turf.lineChunk(contiguousStreets, exactSegDist).features);
            }

            // remove any stray chunks of 0 length (thanks floating point errors)
            chunks = chunks.filter(chunk => Array.isArray(chunk.geometry.coordinates[0]));

            let segOutput =
                {"CurbRamp": {}, "NoCurbRamp": {}, "NoSidewalk": {},"Obstacle": {}, "Occlusion": {}, "SurfaceProblem": {}};
            for (let key in segOutput) {
                if (segOutput.hasOwnProperty(key)) {
                    segOutput[key] = [];
                    for (let i = 0; i < chunks.length; i++) {
                        segOutput[key][i] = {};
                        for (let j = 0; j < turkers.length; j++) {
                            segOutput[key][i][turkers[j]] = 0;
                        }
                    }
                }
            }

            // for each cluster, find the label nearest the center of the cluster, find which segment this label is
            // nearest, and mark all labels from that cluster as being in that segment
            let clusterIds = [...new Set(labs.map(label => label.properties.cluster_id))];
            for (let clustIndex = 0; clustIndex < clusterIds.length; clustIndex++) {
                let currLabels = labs.filter(label => label.properties.cluster_id === clusterIds[clustIndex]);
                if (REMOVE_LOW_SEVERITY && ["Obstacle", "SurfaceProblem"].indexOf(currLabels[0].properties.label_type) > 0) {
                    currLabels = currLabels.filter(label => label.properties.temporary !== false);
                }
                if (currLabels.length > 0) {
                    let centerPoint = turf.centerOfMass({"features": currLabels, "type": "FeatureCollection"});
                    let repLabel = turf.nearest(centerPoint, {"features": currLabels, "type": "FeatureCollection"});
                    let currType = repLabel.properties.label_type;

                    // trying to exclude low severity surface problems and obstacles
                    // if (["SurfaceProblem", "Obstacle"].indexOf(currLabel.properties.label_type) >= 0 && currLabel.properties.severity > 3) {
                    // get closest segment to this label
                    // http://turfjs.org/docs/#pointonline
                    let chunkIndex;
                    let minDist = Number.POSITIVE_INFINITY;
                    for (let i = 0; i < chunks.length; i++) {
                        let closestPoint = turf.pointOnLine(chunks[i], repLabel);
                        if (closestPoint.properties.dist < minDist) {
                            chunkIndex = i;
                            minDist = closestPoint.properties.dist;
                        }
                    }

                    // increment this segment's count of labels (of this label type), distributing labels based on turker_id
                    for (let turkerIndex = 0; turkerIndex < turkers.length; turkerIndex++) {
                        let turkerId = turkers[turkerIndex];
                        let labelCount = currLabels.filter(label => label.properties.turker_id === turkerId).length;
                        if (BINARY) {
                            let curr = segOutput[currType][chunkIndex][turkerId];
                            segOutput[currType][chunkIndex][turkerId] = Math.max(curr, Math.min(labelCount, 1));
                        } else {
                            segOutput[currType][chunkIndex][turkerId] += labelCount;
                        }
                    }
                }
            }
            output[hitIndex][String(segDist * 1000) + "_meter"] = segOutput;
        }
    }

    // combine the results from all the hits into a single, condensed object to be output as CSV
    let out = {};
    for (let level in output[0]) {
        if (output[0].hasOwnProperty(level)) {
            out[level] = {"CurbRamp": {}, "NoCurbRamp": {}, "NoSidewalk": {},"Obstacle": {}, "Occlusion": {}, "SurfaceProblem": {}};
            for (let label_type in out[level]) {
                if (out[level].hasOwnProperty(label_type)) {
                    out[level][label_type] = [];
                    for (let j = 0; j < output.length; j++) {
                        out[level][label_type] = out[level][label_type].concat(output[j][level][label_type]);
                    }
                }
            }
        }
    }
    return out;
}

function convertToCSV(objArray) {
    let array = typeof objArray !== 'object' ? JSON.parse(objArray) : objArray;
    let str = '';

    for (let i = 0; i < array.length; i++) {
        let line = '';
        for (let index in array[i]) {
            if (line !== '') line += ',';
            line += array[i][index];
        }
        str += line + '\r\n';
    }
    return str;
}

function exportCSVFile(items, fileTitle) {

    // Convert Object to JSON
    let jsonObject = JSON.stringify(items);

    let csv = this.convertToCSV(jsonObject);

    let exportedFilename = fileTitle + '.csv';

    let blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    if (navigator.msSaveBlob) { // IE 10+
        navigator.msSaveBlob(blob, exportedFilename);
    } else {
        let link = document.createElement("a");
        if (link.download !== undefined) { // feature detection
            // Browsers that support HTML5 download attribute
            let url = URL.createObjectURL(blob);
            link.setAttribute("href", url);
            link.setAttribute("download", exportedFilename);
            link.style.visibility = 'hidden';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }
    }
}

// TODO Takes the results of the IRR setup and outputs the CSVs on the client machine. Maybe all in a .tar or something?
function outputData(outputJson) {

    for (let category in outputJson) {
        if (outputJson.hasOwnProperty(category)) {
            console.log(category);
            console.log(outputJson[category]);

            let categoryJson = outputJson[category];
            for (let labelType in categoryJson) {
                if (categoryJson.hasOwnProperty(labelType)) {
                    let fileTitle = category + '_' + labelType;
                    console.log(labelType + ' ' + fileTitle);

                    // Call the exportCSVFile() to trigger the download
                    exportCSVFile(categoryJson[labelType], fileTitle);
                }
            }
        }
    }

}

function IRR(data, turf) {
    console.log("Data received: ", data);
    let output = setupIRR(data);
    console.log(output);
    outputData(output);
}