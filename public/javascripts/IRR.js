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

    for(let hitIndex = 0; hitIndex < hits.length; hitIndex++) {
        let currHit = hits[hitIndex];
        console.log(currHit);
        let routes = [...new Set(labelsData.features.filter(label => label.properties.hit_id === currHit).map(label => label.properties.route_id))];
        let labs = labelsData.features.filter(label => label.properties.hit_id === currHit);

        // TODO check that the extra streets that have been removed do not actually have labels by them
        // TODO make it so that it isn't assumed that the routes streets are in order
        let streets = streetsData.features.filter(street => routes.indexOf(street.properties.route_id) >= 0);
        // console.log(routes);
        // console.log(streets.length);
        // check that each mission has the right distance

        for (let routeIndex = 0; routeIndex < routes.length; routeIndex++) {
            let thisRouteStreets = streets.filter(street => street.properties.route_id === routes[routeIndex]);
            // console.log(thisRouteStreets.length);
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
            // console.log(streets.length);
        }
        // console.log(streets.length);

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

        // TODO keep clustered labels together
        for(let labIndex = 0; labIndex < labs.length; labIndex++) {
            // let currLabel = turf.point([labelsData[labIndex].lng, labelsData[labIndex].lat]);
            let currLabel = labs[labIndex];

            // get closest street to this label
            // http://turfjs.org/docs/#pointonline
            let segIndex;
            let minDist = Number.POSITIVE_INFINITY;
            for (let i = 0; i < streets.length; i++) {
                let closestPoint = turf.pointOnLine(streets[i], currLabel);
                if (closestPoint.properties.dist < minDist) {
                    segIndex = i;
                    minDist = closestPoint.properties.dist;
                }
            }

            // increment this street's count of labels (of this label type)
            streetOutput[currLabel.properties.label_type][segIndex][currLabel.properties.turker_id] += 1;

        }
        console.log(streets);
        output[hitIndex].street = streetOutput;


        // segment level
        // combine streets into a set of contiguous linestrings
        // http://turfjs.org/docs/#combine -- combines the different streets into a single MultiLineString
        // http://turfjs.org/docs/#lineintersect -- lets you know the points where two lines intersect
        let combinedStreets = turf.combine({"features": streets, "type": "FeatureCollection"});
        // console.log(streets[0]);
        // console.log(turf.lineDistance(streets[0]));
        // console.log(turf.lineChunk(streets[0], 0.005));


        let segDists = [0.005, 0.01]; // in meters
        for(let segDistIndex = 0; segDistIndex < segDists.length; segDistIndex++) {
            let segDist = segDists[segDistIndex];

            // split streets into a bunch of little segments based on segDist and length of each contiguous segment
            // TODO make sure that each contiguous segment is done separately
            // TODO pick actual distance for each contiguous segment separately so that all segs are approx equal
            // http://turfjs.org/docs/#along -- to remove end of a street edge at end of HIT
            // http://turfjs.org/docs/#linechunk
            console.log("blah");
            let chunks = turf.lineChunk(combinedStreets, segDist).features;
            // console.log(chunks);
            let sum1 = 0.0;
            for (let i = 0; i < chunks.length; i++) sum1 += turf.lineDistance(chunks[i]);
            let sum2 = 0.0;
            for (let i = 0; i < combinedStreets.length; i++) sum2 += turf.lineDistance(combinedStreets[i]);
            let sum3 = 0;
            let r1 = streets.filter(street => street.properties.route_id === routes[0]);
            for (let i = 0; i < r1.length; i++) sum3 += turf.lineDistance(r1[i]);
            let sum4 = 0;
            let r2 = streets.filter(street => street.properties.route_id === routes[1]);
            for (let i = 0; i < r2.length; i++) sum4 += turf.lineDistance(r2[i]);
            let sum5 = 0;
            let r3 = streets.filter(street => street.properties.route_id === routes[2]);
            for (let i = 0; i < r3.length; i++) sum5 += turf.lineDistance(r3[i]);
            console.log(sum3);
            console.log(sum4);
            console.log(sum5);

            console.log(sum1);
            console.log(turf.lineDistance(combinedStreets));

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

            // TODO keep clustered labels together
            for(let labIndex = 0; labIndex < labs.length; labIndex++) {
                let currLabel = labs[labIndex];

                // get closest segment to this label
                // http://turfjs.org/docs/#pointonline
                let chunkIndex;
                let minDist = Number.POSITIVE_INFINITY;
                for (let i = 0; i < chunks.length; i++) {
                    let closestPoint = turf.pointOnLine(chunks[i], currLabel);
                    if (closestPoint.properties.dist < minDist) {
                        chunkIndex = i;
                        minDist = closestPoint.properties.dist;
                    }
                }

                // increment this segment's count of labels (of this label type)
                segOutput[currLabel.properties.label_type][chunkIndex][currLabel.properties.turker_id] += 1;

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