const BINARY = true;
const REMOVE_LOW_SEVERITY = false;
const PROB_NO_PROB = false;


// Split streets into a bunch of little segments based on segDist and length of each contiguous segment. For
// each contiguous set of streets, split the contiguous line up into equally sized segments, as close as
// possible to segDist.
// http://turfjs.org/docs/#linechunk
function splitIntoChunks(streets, segDist) {
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
    return chunks;
}

function getLabelCountsBySegment(chunks, gtLabs, workerLabs, majorityThresh) {

    let segOutput = {};
    if (PROB_NO_PROB) {
        segOutput = {"Problem": {}};
    } else {
        segOutput = {"CurbRamp": {}, "NoCurbRamp": {}, "NoSidewalk": {},"Obstacle": {}, "Occlusion": {}, "SurfaceProblem": {}};
    }

    for (let labelType in segOutput) {
        if (segOutput.hasOwnProperty(labelType)) {
            segOutput[labelType] = [];
            for (let i = 0; i < chunks.length; i++) {
                segOutput[labelType][i] = {"gt": 0, "worker": 0};
            }
        }
    }

    let setsOfLabels = {"gt": gtLabs, "worker": workerLabs};
    for (let labelSource in setsOfLabels) {
        if (setsOfLabels.hasOwnProperty(labelSource)) {
            let labs = setsOfLabels[labelSource];

            // for each cluster, find the label nearest the center of the cluster, find which segment this label is
            // nearest, and mark all labels from that cluster as being in that segment
            let clusterIds = [...new Set(labs.map(label => label.properties.cluster_id))];
            for (let clustIndex = 0; clustIndex < clusterIds.length; clustIndex++) {
                let currLabels = labs.filter(label => label.properties.cluster_id === clusterIds[clustIndex]);
                if (REMOVE_LOW_SEVERITY && ["Obstacle", "SurfaceProblem"].indexOf(currLabels[0].properties.label_type) > 0) {
                    currLabels = currLabels.filter(label => label.properties.temporary !== false);
                }
                if (labelSource === "gt" || currLabels.length >= majorityThresh) {
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

                    // TODO don't include volunteer labels that are way past the end of the final segment
                    // increment this segment's count of labels (of this label type), distributing labels based on turker_id
                    let labelCount = currLabels.length;
                    if (PROB_NO_PROB) {
                        if (BINARY) {
                            let curr = segOutput["Problem"][chunkIndex][labelSource];
                            segOutput["Problem"][chunkIndex][labelSource] = Math.max(curr, Math.min(labelCount, 1));
                        } else {
                            segOutput["Problem"][chunkIndex][labelSource] += labelCount;
                        }
                    } else {
                        if (BINARY) {
                            let curr = segOutput[currType][chunkIndex][labelSource];
                            segOutput[currType][chunkIndex][labelSource] = Math.max(curr, Math.min(labelCount, 1));
                        } else {
                            segOutput[currType][chunkIndex][labelSource] += labelCount;
                        }
                    }
                }
            }

        }
    }
    return segOutput;
}

function clipStreets(streetsData, routes) {

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
                // If this is the last street in a route, remove extra bit at end of the street that isn't audited
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
    return streets;
}

// Takes a set of points and a set of street geometries. Fits labels to those streets, giving counts of how many labels
// of each label type are closest to each street. Streets are then also split up into smaller line segments, and the
// same counts are then tabulated for each of those segments.
function setupAccuracy(data, clusterNum) {
    // unpack different pieces of data
    let streetsData = data.streets;
    let gtLabelData = data.gt_labels;
    let workerLabelData = data.worker_labels;
    // gets unique set of conditions that workers have completed
    // let conditions = [...new Set(workerLabelData.features.map(label => label.properties.condition_id))];
    // let conditions = [72, 74, 85, 98, 100, 120, 122, 128, 131, 134, 136, 138];
    let conditions = [72, 74, 98, 100, 122, 128];

    // remove "Other" label type for now since there are none of them in GT
    // TODO decide if we want to do some analysis of the "Other" label type
    let labelsToAnalyze = ["CurbRamp", "NoCurbRamp", "NoSidewalk", "Obstacle", "Occlusion", "SurfaceProblem"];
    workerLabelData.features = workerLabelData.features.filter(label => labelsToAnalyze.indexOf(label.properties.label_type) >= 0);

    // if we are only looking at 1 worker, they haven't gone through clustering, so just assign incrementing cluster ids
    let majorityThresh = Math.ceil(parseInt(clusterNum) / 2.0);
    if (parseInt(clusterNum) === 1) {
        for (let i = 0; i < workerLabelData.features.length; i++) {
            workerLabelData.features[i].properties.cluster_id = i;
        }
    }
    // but we always have only one of each gt label, so do the same thing for gt labels for now...
    // TODO do something more elegant, better than just saying every GT label gets its own cluster
    for (let i = 0; i < gtLabelData.features.length; i++) {
        gtLabelData.features[i].properties.cluster_id = i;
    }

    let output = [];
    for(let i = 0; i < conditions.length; i++) output[i] = {};


    // print out label counts by type
    let gtLabelCounts = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0};
    for (let i = 0; i < gtLabelData.features.length; i++) {
        gtLabelCounts[gtLabelData.features[i].properties.label_type] += 1;
    }
    console.log(gtLabelCounts);
    let workerLabelCounts = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0};
    for (let i = 0; i < workerLabelData.features.length; i++) {
        workerLabelCounts[workerLabelData.features[i].properties.label_type] += 1;
    }
    console.log(workerLabelCounts);


    for(let conditionIndex = 0; conditionIndex < conditions.length; conditionIndex++) {
        let currCondition = conditions[conditionIndex];
        let routes = [...new Set(gtLabelData.features.filter(label => label.properties.condition_id === currCondition).map(label => label.properties.route_id))];
        let gtLabs = gtLabelData.features.filter(label => label.properties.condition_id === currCondition);
        let workerLabs = workerLabelData.features.filter(label => label.properties.condition_id === currCondition);

        // print out label counts by type
        let gtLabelCounts2 = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0};
        for (let i = 0; i < gtLabs.length; i++) {
            gtLabelCounts2[gtLabs[i].properties.label_type] += 1;
        }
        console.log(gtLabelCounts2);
        let workerLabelCounts2 = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0};
        for (let i = 0; i < workerLabs.length; i++) {
            workerLabelCounts2[workerLabs[i].properties.label_type] += 1;
        }
        console.log(workerLabelCounts2);

        let streets = clipStreets(streetsData, routes);

        output[conditionIndex].street = getLabelCountsBySegment(streets, gtLabs, workerLabs, majorityThresh);


        let segDists = [0.005, 0.01]; // in kilometers
        for(let segDistIndex = 0; segDistIndex < segDists.length; segDistIndex++) {
            let segDist = segDists[segDistIndex];
            let chunks = splitIntoChunks(streets, segDist);
            output[conditionIndex][String(segDist * 1000) + "_meter"] = getLabelCountsBySegment(chunks, gtLabs, workerLabs, majorityThresh);
        }
    }
    console.log(output);

    // combine the results from all the conditions into a single, condensed object to be output as CSV
    // let out = {};
    // for (let level in output[0]) {
    //     if (output[0].hasOwnProperty(level)) {
    //         if (PROB_NO_PROB) {
    //             out[level] = {"Problem": {}};
    //         } else {
    //             out[level] = {"CurbRamp": {}, "NoCurbRamp": {}, "NoSidewalk": {},"Obstacle": {}, "Occlusion": {}, "SurfaceProblem": {}};
    //         }
    //         for (let labelType in out[level]) {
    //             if (out[level].hasOwnProperty(labelType)) {
    //                 out[level][labelType] = [];
    //                 for (let j = 0; j < 2; j++) {
    //                     out[level][labelType] = out[level][labelType].concat(output[j][level][labelType]);
    //                 }
    //             }
    //         }
    //     }
    // }
    return output;
}

function calculateAccuracy(counts) {
    console.log(counts);

    // set up average accuracy data structure
    let aveAccuracies = {};
    let definedAccuracyCounts = {};
    for (let granularity in counts[0]) {
        if (counts[0].hasOwnProperty(granularity)) {
            aveAccuracies[granularity] = {};
            definedAccuracyCounts[granularity] = {};
            for (let labelType in counts[0][granularity]) {
                if (counts[0][granularity].hasOwnProperty(labelType)) {
                    aveAccuracies[granularity][labelType] = {precision: 0, recall: 0, specificity: 0, f_measure: 0};
                    definedAccuracyCounts[granularity][labelType] = {precision: 0, recall: 0, specificity: 0, f_measure: 0};
                }
            }
        }
    }

    let trueFalsePosNegCounts = [];
    let accuracies = [];
    for (let conditionIndex = 0; conditionIndex < counts.length; conditionIndex++) {
        accuracies[conditionIndex] = {};
        trueFalsePosNegCounts[conditionIndex] = {};
        for (let granularity in counts[conditionIndex]) {
            if (counts[conditionIndex].hasOwnProperty(granularity)) {
                accuracies[conditionIndex][granularity] = {};
                trueFalsePosNegCounts[conditionIndex][granularity] = {};
                for (let labelType in counts[conditionIndex][granularity]) {
                    if (counts[conditionIndex][granularity].hasOwnProperty(labelType)) {
                        let setOfCounts = counts[conditionIndex][granularity][labelType];

                        // count up number of true/false positives/negatives
                        let truePos = 0;
                        let trueNeg = 0;
                        let falsePos = 0;
                        let falseNeg = 0;
                        for (let segIndex = 0; segIndex < setOfCounts.length; segIndex++) {
                            truePos += Math.min(setOfCounts[segIndex].gt, setOfCounts[segIndex].worker);
                            falsePos += Math.max(0, setOfCounts[segIndex].worker - setOfCounts[segIndex].gt);
                            falseNeg += Math.max(0, setOfCounts[segIndex].gt - setOfCounts[segIndex].worker);
                            if (Math.max(setOfCounts[segIndex].gt, setOfCounts[segIndex].worker) === 0) {
                                trueNeg += 1;
                            }
                        }
                        trueFalsePosNegCounts[conditionIndex][granularity][labelType] = {
                            truePos: truePos, trueNeg: trueNeg, falsePos: falsePos, falseNeg: falseNeg
                        };

                        // calculate accuracy measures for this label type in this condition
                        let precision = truePos / (truePos + falsePos); // precision
                        let recall = truePos / (truePos + falseNeg); // recall (sensitivity, true pos rate)
                        let specificity = trueNeg / (trueNeg + falsePos); // true neg rate (specificity)
                        let fMeasure = 2 * precision * recall / (precision + recall);
                        accuracies[conditionIndex][granularity][labelType] = {
                            precision: precision, recall: recall, specificity: specificity, f_measure: fMeasure
                        };

                        // add accuracy measures to sums of them across conditions so an average can be taken
                        // TODO move this whole thing out into its own function called getAccuracySummaryStats
                        aveAccuracies[granularity][labelType].precision += precision ? precision : 0;
                        aveAccuracies[granularity][labelType].recall += recall ? recall : 0;
                        aveAccuracies[granularity][labelType].specificity += specificity ? specificity : 0;
                        aveAccuracies[granularity][labelType].f_measure += fMeasure ? fMeasure : 0;
                        definedAccuracyCounts[granularity][labelType].precision += precision ? 1 : 0;
                        definedAccuracyCounts[granularity][labelType].recall += recall ? 1 : 0;
                        definedAccuracyCounts[granularity][labelType].specificity += specificity ? 1 : 0;
                        definedAccuracyCounts[granularity][labelType].f_measure += fMeasure ? 1 : 0;
                    }
                }
            }
        }
    }
    console.log(trueFalsePosNegCounts);
    console.log(accuracies);

    // calculate final average
    for (let granularity in aveAccuracies) {
        if (aveAccuracies.hasOwnProperty(granularity)) {
            for (let labelType in aveAccuracies[granularity]) {
                if (aveAccuracies[granularity].hasOwnProperty(labelType)) {
                    aveAccuracies[granularity][labelType].precision /= definedAccuracyCounts[granularity][labelType].precision;
                    aveAccuracies[granularity][labelType].recall /= definedAccuracyCounts[granularity][labelType].recall;
                    aveAccuracies[granularity][labelType].specificity /= definedAccuracyCounts[granularity][labelType].specificity;
                    aveAccuracies[granularity][labelType].f_measure /= definedAccuracyCounts[granularity][labelType].f_measure;
                }
            }
        }
    }
    console.log(definedAccuracyCounts);
    console.log(aveAccuracies);

    return accuracies;
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

function Accuracy(data, clusterNum, turf) {
    console.log("Data received: ", data);
    let output = setupAccuracy(data, clusterNum);
    // console.log(output);
    calculateAccuracy(output);
    // outputData(output);
}