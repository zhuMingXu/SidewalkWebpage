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

function getLabelCountsBySegment(chunks, gtLabs, workerLabs, workerThresh, options) {

    // unpack optional arguments
    options = options || {};
    let binary = options.binary || false;
    let includedSeverity = options.included_severity || "";

    let isInSeverityRange = {
        "all": function(severity) { return true },
        "<=2": function(severity) { return severity <= 2 },
        ">=3": function(severity) { return severity >= 3 },
        "<=3": function(severity) { return severity <= 3 },
        ">=4": function(severity) { return severity >= 4 }
    };
    let severityCheck = isInSeverityRange[includedSeverity];

    let segOutput = {"CurbRamp": {}, "NoCurbRamp": {}, "NoSidewalk": {},"Obstacle": {}, "Occlusion": {}, "SurfaceProblem": {}, "Problem": {}};
    let problemLabelTypes = ["NoCurbRamp", "Obstacle", "SurfaceProblem"];

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

            // For each cluster, find the label nearest the center of the cluster, find which segment this label is
            // nearest, and mark all labels from that cluster as being in that segment
            let clusterIds = [...new Set(labs.map(label => label.properties.cluster_id))];
            for (let clustIndex = 0; clustIndex < clusterIds.length; clustIndex++) {
                let currLabels = labs.filter(label => label.properties.cluster_id === clusterIds[clustIndex]);

                let typesWithSeverity = ["Obstacle", "SurfaceProblem", "NoCurbRamp", "Problem"];
                if (labelSource === "gt" && typesWithSeverity.indexOf(currLabels[0].properties.label_type) >= 0) {
                    // currLabels = currLabels.filter(label => label.properties.temporary !== false);
                    currLabels = currLabels.filter(label => severityCheck(label.properties.severity));
                }

                if ((currLabels.length >= 1 && labelSource === "gt") || currLabels.length >= workerThresh) {
                    let centerPoint = turf.centerOfMass({"features": currLabels, "type": "FeatureCollection"});
                    // let repLabel = turf.nearest(centerPoint, {"features": currLabels, "type": "FeatureCollection"});
                    let currType = currLabels[0].properties.label_type;

                    // get closest segment to this label
                    // http://turfjs.org/docs/#pointonline
                    let chunkIndex;
                    let minDist = Number.POSITIVE_INFINITY;
                    for (let i = 0; i < chunks.length; i++) {
                        let closestPoint = turf.pointOnLine(chunks[i], centerPoint);
                        if (closestPoint.properties.dist < minDist) {
                            chunkIndex = i;
                            minDist = closestPoint.properties.dist;
                        }
                    }

                    // TODO don't include volunteer labels that are way past the end of the final segment
                    // Increment this segment's count of labels (for this label type). Since we don't run clustering on
                    // GT labels, we have to manually mark the Problem label type for GT ourselves.
                    if (binary) {
                        segOutput[currType][chunkIndex][labelSource] = 1;
                        if (labelSource === "gt" && problemLabelTypes.indexOf(currType) >= 0) {
                            segOutput["Problem"][chunkIndex][labelSource] = 1;
                        }
                    } else {
                        segOutput[currType][chunkIndex][labelSource] += 1;
                        if (labelSource === "gt" && problemLabelTypes.indexOf(currType) >= 0) {
                            segOutput["Problem"][chunkIndex][labelSource] += 1;
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


function clipAllStreets(data) {
    let streetsData = data.streets;
    let gtLabelData = data.gt_labels;

    let conditions = Array.from(new Array(71), (x,i) => i + 70).filter(c => notReady.indexOf(c) < 0);

    for(let conditionIndex = 0; conditionIndex < conditions.length; conditionIndex++) {
        let currCondition = conditions[conditionIndex];
        let routes = [...new Set(gtLabelData.features.filter(label => label.properties.condition_id === currCondition).map(label => label.properties.route_id))];

        streetsData = clipStreets(streetsData, routes);

    }
}


// Takes a set of points and a set of street geometries. Fits labels to those streets, giving counts of how many labels
// of each label type are closest to each street. Streets are then also split up into smaller line segments, and the
// same counts are then tabulated for each of those segments.
function setupAccuracy(data, clusterNum, options) {

    // unpack optional arguments
    options = options || {};
    let binary = options.binary || false;
    let includedSeverity = options.included_severity || "";
    let workerThresh = options.worker_thresh || Math.ceil(parseInt(clusterNum) / 2.0);

    let futureOpts = {
        binary: binary,
        included_severity: includedSeverity,
        worker_thresh: workerThresh
    };

    // unpack different pieces of data
    let streetsData = data.streets;
    let gtLabelData = data.gt_labels;
    let workerLabelData = data.worker_labels;
    let workerData = data.workers;
    // gets unique set of conditions that workers have completed
    // let conditions = [...new Set(workerLabelData.features.map(label => label.properties.condition_id))];
    // let conditions = [72, 74, 85, 98, 100, 120, 122, 128, 131, 134, 136, 138];
	// let conditions = [72, 74, 98, 100, 122, 128]; // a few conditions for testing
    let notReady = [71, 104, 123, 124, 138];
    let conditions = Array.from(new Array(71), (x,i) => i + 70).filter(c => notReady.indexOf(c) < 0);
    // let conditions = [72]; // for testing
    // let conditions = [73]; // for testing

    // remove "Other" label type for now since there are none of them in GT
    let labelsToAnalyze = ["CurbRamp", "NoCurbRamp", "NoSidewalk", "Obstacle", "Occlusion", "SurfaceProblem", "Problem"];
    let problemLabelTypes = ["NoCurbRamp", "Obstacle", "SurfaceProblem"];
    workerLabelData.features = workerLabelData.features.filter(label => labelsToAnalyze.indexOf(label.properties.label_type) >= 0);

    // if we are only looking at 1 worker, they haven't gone through clustering, so just assign incrementing cluster ids
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
    let gtLabelCounts = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0, "Problem": 0};
    for (let i = 0; i < gtLabelData.features.length; i++) {
        gtLabelCounts[gtLabelData.features[i].properties.label_type] += 1;
        if (problemLabelTypes.indexOf(gtLabelData.features[i].properties.label_type) >= 0) {
            gtLabelCounts["Problem"] += 1;
        }
    }
    // console.log(gtLabelCounts);
    let workerLabelCounts = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0, "Problem": 0};
    for (let i = 0; i < workerLabelData.features.length; i++) {
        workerLabelCounts[workerLabelData.features[i].properties.label_type] += 1;
    }
    // console.log(workerLabelCounts);


    for(let conditionIndex = 0; conditionIndex < conditions.length; conditionIndex++) {
        let currCondition = conditions[conditionIndex];
        let routes = [...new Set(gtLabelData.features.filter(label => label.properties.condition_id === currCondition).map(label => label.properties.route_id))];
        let gtLabs = gtLabelData.features.filter(label => label.properties.condition_id === currCondition);
        let workerLabs = workerLabelData.features.filter(label => label.properties.condition_id === currCondition);
        // let workerIds = [...new Set(workerLabs.map(label => label.properties.worker_id))];
        let workerIds = workerData.filter(w => w.condition_id === currCondition)[0].worker_ids;

        output[conditionIndex].condition_id = currCondition;
        output[conditionIndex].workers = workerIds;
        output[conditionIndex].n_workers = clusterNum;
        output[conditionIndex].worker_thresh = workerThresh;
        output[conditionIndex].binary = binary;
        output[conditionIndex].included_severity = includedSeverity;

        // print out label counts by type
        let gtLabelCounts2 = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0, "Problem": 0};
        for (let i = 0; i < gtLabs.length; i++) {
            gtLabelCounts2[gtLabs[i].properties.label_type] += 1;
            if (problemLabelTypes.indexOf(gtLabs[i].properties.label_type) >= 0) {
                gtLabelCounts2["Problem"] += 1;
            }
        }
        // console.log(gtLabelCounts2);
        let workerLabelCounts2 = {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0, "Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0, "Problem": 0};
        for (let i = 0; i < workerLabs.length; i++) {
            workerLabelCounts2[workerLabs[i].properties.label_type] += 1;
        }
        // console.log(workerLabelCounts2);

        let streets = clipStreets(streetsData, routes);

        output[conditionIndex].street = getLabelCountsBySegment(streets, gtLabs, workerLabs, workerThresh, futureOpts);


        let segDists = [0.005, 0.01]; // in kilometers
        for(let segDistIndex = 0; segDistIndex < segDists.length; segDistIndex++) {
            let segDist = segDists[segDistIndex];
            let chunks = splitIntoChunks(streets, segDist);
            output[conditionIndex][String(segDist * 1000) + "_meter"] =
                getLabelCountsBySegment(chunks, gtLabs, workerLabs, workerThresh, futureOpts);
        }
    }
    console.log("Output:");
    console.log(output);

    return output;
}

function calculateAccuracy(counts) {
    console.log(counts);

    let granularities = ["5_meter", "10_meter", "street"];
    // set up average accuracy data structure
    let aveAccuracies = {};
    let definedAccuracyCounts = {};
    for (let granularityIndex = 0; granularityIndex < granularities.length; granularityIndex++) {
        let granularity = granularities[granularityIndex];
        aveAccuracies[granularity] = {};
        definedAccuracyCounts[granularity] = {};
        for (let labelType in counts[0][granularity]) {
            if (counts[0][granularity].hasOwnProperty(labelType)) {
                aveAccuracies[granularity][labelType] = {precision: 0, recall: 0, specificity: 0, f_measure: 0};
                definedAccuracyCounts[granularity][labelType] = {
                    precision: 0,
                    recall: 0,
                    specificity: 0,
                    f_measure: 0
                };
            }
        }
    }

    let trueFalsePosNegCounts = [];
    let accuracies = [];
    for (let conditionIndex = 0; conditionIndex < counts.length; conditionIndex++) {
        accuracies[conditionIndex] = {};
        trueFalsePosNegCounts[conditionIndex] = {};
        accuracies[conditionIndex].workers = counts[conditionIndex].workers;
        accuracies[conditionIndex].condition_id = counts[conditionIndex].condition_id;
        accuracies[conditionIndex].n_workers = counts[conditionIndex].n_workers;
        accuracies[conditionIndex].worker_thresh = counts[conditionIndex].worker_thresh;
        accuracies[conditionIndex].binary = counts[conditionIndex].binary;
        accuracies[conditionIndex].included_severity = counts[conditionIndex].included_severity;
        for (let granularityIndex = 0; granularityIndex < granularities.length; granularityIndex++) {
            let granularity = granularities[granularityIndex];
            accuracies[conditionIndex][granularity] = {};
            trueFalsePosNegCounts[conditionIndex][granularity] = {};
            for (let labelType in counts[conditionIndex][granularity]) {
                if (counts[conditionIndex][granularity].hasOwnProperty(labelType)) {
                    let setOfCounts = counts[conditionIndex][granularity][labelType];

                    // Count up number of true/false positives/negatives
                    let truePos = 0;
                    let trueNeg = 0;
                    let falsePos = 0;
                    let falseNeg = 0;
                    // These counts work in both binary and ordinal case.
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
                        truePos: truePos, trueNeg: trueNeg, falsePos: falsePos, falseNeg: falseNeg,
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
    console.log(trueFalsePosNegCounts);
    console.log(accuracies);

    // calculate final average
    for (let granularityIndex = 0; granularityIndex < granularities.length; granularityIndex++) {
        let granularity = granularities[granularityIndex];
        for (let labelType in aveAccuracies[granularity]) {
            if (aveAccuracies[granularity].hasOwnProperty(labelType)) {
                aveAccuracies[granularity][labelType].precision /= definedAccuracyCounts[granularity][labelType].precision;
                aveAccuracies[granularity][labelType].recall /= definedAccuracyCounts[granularity][labelType].recall;
                aveAccuracies[granularity][labelType].specificity /= definedAccuracyCounts[granularity][labelType].specificity;
                aveAccuracies[granularity][labelType].f_measure /= definedAccuracyCounts[granularity][labelType].f_measure;
            }
        }
    }
    console.log(definedAccuracyCounts);
    console.log(aveAccuracies);

    return accuracies;
}


function convertToCSV(resultObjects) {
    let str =
        'condition.id,' +
        'worker1,worker2,worker3,worker4,worker5,' +
        'n.workers,' +
        'worker.thresh,' +
        'binary,' +
        'included.severity,' +
        'granularity,' +
        'label.type,' +
        'true.pos,false.pos,true.neg,false.neg,' +
        'precision,recall,specificity,f.measure\r\n';
    let granularities = ["5_meter", "10_meter", "street"];

    let outerArray = typeof resultObjects !== 'object' ? JSON.parse(resultObjects) : resultObjects;

    for (let objectIndex = 0; objectIndex < outerArray.length; objectIndex++) {
        let array = outerArray[objectIndex];
        for (let i = 0; i < array.length; i++) {
            // let granularities = keys(array[i]);
            for (let granularityIndex = 0; granularityIndex < granularities.length; granularityIndex++) {
                let granularity = granularities[granularityIndex];
                for (let labelType in array[i][granularity]) {
                    if (array[i][granularity].hasOwnProperty(labelType)) {
                        let line = String(array[i].condition_id);
                        line += ",";
                        line += array[i].workers[0] ? array[i].workers[0].replace(",", "---") : null;
                        line += ",";
                        line += array[i].workers[1] ? array[i].workers[1].replace(",", "---") : null;
                        line += ",";
                        line += array[i].workers[2] ? array[i].workers[2].replace(",", "---") : null;
                        line += ",";
                        line += array[i].workers[3] ? array[i].workers[3].replace(",", "---") : null;
                        line += ",";
                        line += array[i].workers[4] ? array[i].workers[4].replace(",", "---") : null;
                        line += "," + array[i].n_workers;
                        line += "," + array[i].worker_thresh;
                        line += "," + String(array[i].binary);
                        line += "," + String(array[i].included_severity);
                        line += "," + granularity;
                        line += "," + labelType;
                        line += "," + array[i][granularity][labelType].truePos;
                        line += "," + array[i][granularity][labelType].falsePos;
                        line += "," + array[i][granularity][labelType].trueNeg;
                        line += "," + array[i][granularity][labelType].falseNeg;
                        line += "," + array[i][granularity][labelType].precision;
                        line += "," + array[i][granularity][labelType].recall;
                        line += "," + array[i][granularity][labelType].specificity;
                        line += "," + array[i][granularity][labelType].f_measure;
                        str += line + '\r\n';
                    }
                }
            }
        }
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

function Accuracy(data, clusterNum, turf) {
    console.log("Data received: ", data);
    let output = setupAccuracy(data, clusterNum);
    let accuracies = calculateAccuracy(output);
    exportCSVFile([accuracies], "accuracies.csv")
}


let allVolunteerButton = document.getElementById('allVolunteerAccuracy');
allVolunteerButton.onclick = function() {

    // Get data
    $.getJSON("/accuracyDataWithSinglePersonClust/volunteer/1", function (volunteerData) {
	// $.getJSON("/accuracyData/volunteer/1", function (volunteerData) {
        let accuracyOutputArray = [];
        let optsArray =
            [
                { binary: true, included_severity: "all" },
                { binary: true, included_severity: "<=2" },
                { binary: true, included_severity: ">=3" },
                { binary: true, included_severity: "<=3" },
                { binary: true, included_severity: ">=4" },
                { binary: false, included_severity: "all" },
                { binary: false, included_severity: "<=2" },
                { binary: false, included_severity: ">=3" },
                { binary: false, included_severity: "<=3" },
                { binary: false, included_severity: ">=4" }
            ];
        console.log(volunteerData);

        for (let i = 0; i < optsArray.length; i++) {

            // let volunteerDataCopy = $.extend({}, volunteerData);
            // let volunteerDataCopy = JSON.parse(JSON.stringify(volunteerData));
            let output = setupAccuracy(volunteerData, 1, optsArray[i]);
            accuracyOutputArray[i] = calculateAccuracy(output);
            console.log("" + (i + 1) + " down, " + (optsArray.length - i - 1) + " to go!");
        }

        // export CSV
        exportCSVFile(accuracyOutputArray, "accuracies-volunteer");

        $("#all-accuracy-result").html("Success! Enjoy your CSV!");
    });
};


let allTurkerButton = document.getElementById('allTurkerAccuracy');
allTurkerButton.onclick = function() {

    // 1 turker
    $.getJSON("/accuracyDataWithSinglePersonClust/turker/1", function (oneTurkerData) {
    // $.getJSON("/accuracyData/turker/1", function (oneTurkerData) {
        let accuracyOutputArray = [];
        let optsArrayOneTurker =
            [
                { binary: true, included_severity: "all" },
                { binary: true, included_severity: "<=2" },
                { binary: true, included_severity: ">=3" },
                { binary: true, included_severity: "<=3" },
                { binary: true, included_severity: ">=4" },
                { binary: false, included_severity: "all" },
                { binary: false, included_severity: "<=2" },
                { binary: false, included_severity: ">=3" },
                { binary: false, included_severity: "<=3" },
                { binary: false, included_severity: ">=4" }
            ];
        console.log(oneTurkerData);

        for (let i = 0; i < optsArrayOneTurker.length; i++) {

            let output = setupAccuracy(oneTurkerData, 1, optsArrayOneTurker[i]);
            accuracyOutputArray[i] = calculateAccuracy(output);
            console.log("" + (i + 1) + " down, " + (optsArrayOneTurker.length - i - 1) + " to go!");
        }

        // 3 turkers
        $.getJSON("/accuracyDataWithSinglePersonClust/turker/3", function (threeTurkerData) {
        // $.getJSON("/accuracyData/turker/3", function (threeTurkerData) {
            let optsArrayThreeTurkers =
                [
                    {binary: true, worker_thresh: 1, included_severity: "all"},
                    {binary: true, worker_thresh: 1, included_severity: "<=2"},
                    {binary: true, worker_thresh: 1, included_severity: ">=3"},
                    {binary: true, worker_thresh: 1, included_severity: "<=3"},
                    {binary: true, worker_thresh: 1, included_severity: ">=4"},
                    {binary: true, worker_thresh: 2, included_severity: "all"},
                    {binary: true, worker_thresh: 2, included_severity: "<=2"},
                    {binary: true, worker_thresh: 2, included_severity: ">=3"},
                    {binary: true, worker_thresh: 2, included_severity: "<=3"},
                    {binary: true, worker_thresh: 2, included_severity: ">=4"},
                    {binary: true, worker_thresh: 3, included_severity: "all"},
                    {binary: true, worker_thresh: 3, included_severity: "<=2"},
                    {binary: true, worker_thresh: 3, included_severity: ">=3"},
                    {binary: true, worker_thresh: 3, included_severity: "<=3"},
                    {binary: true, worker_thresh: 3, included_severity: ">=4"},
                    {binary: false, worker_thresh: 1, included_severity: "all"},
                    {binary: false, worker_thresh: 1, included_severity: "<=2"},
                    {binary: false, worker_thresh: 1, included_severity: ">=3"},
                    {binary: false, worker_thresh: 1, included_severity: "<=3"},
                    {binary: false, worker_thresh: 1, included_severity: ">=4"},
                    {binary: false, worker_thresh: 2, included_severity: "all"},
                    {binary: false, worker_thresh: 2, included_severity: "<=2"},
                    {binary: false, worker_thresh: 2, included_severity: ">=3"},
                    {binary: false, worker_thresh: 2, included_severity: "<=3"},
                    {binary: false, worker_thresh: 2, included_severity: ">=4"},
                    {binary: false, worker_thresh: 3, included_severity: "all"},
                    {binary: false, worker_thresh: 3, included_severity: "<=2"},
                    {binary: false, worker_thresh: 3, included_severity: ">=3"},
                    {binary: false, worker_thresh: 3, included_severity: "<=3"},
                    {binary: false, worker_thresh: 3, included_severity: ">=4"}
                ];
            console.log(threeTurkerData);

            let offset = optsArrayOneTurker.length;
            for (let j = 0; j < optsArrayThreeTurkers.length; j++) {

                let output = setupAccuracy(threeTurkerData, 3, optsArrayThreeTurkers[j]);
                accuracyOutputArray[j + offset] = calculateAccuracy(output);
                console.log("" + (j + 1) + " down, " + (optsArrayThreeTurkers.length - j - 1) + " to go!");
            }


            // 5 turkers
            $.getJSON("/accuracyDataWithSinglePersonClust/turker/5", function (fiveTurkerData) {
            // $.getJSON("/accuracyData/turker/5", function (fiveTurkerData) {
                let optsArrayFiveTurkers =
                    [
                        {binary: true, worker_thresh: 1, included_severity: "all"},
                        {binary: true, worker_thresh: 1, included_severity: "<=2"},
                        {binary: true, worker_thresh: 1, included_severity: ">=3"},
                        {binary: true, worker_thresh: 1, included_severity: "<=3"},
                        {binary: true, worker_thresh: 1, included_severity: ">=4"},
                        {binary: true, worker_thresh: 3, included_severity: "all"},
                        {binary: true, worker_thresh: 3, included_severity: "<=2"},
                        {binary: true, worker_thresh: 3, included_severity: ">=3"},
                        {binary: true, worker_thresh: 3, included_severity: "<=3"},
                        {binary: true, worker_thresh: 3, included_severity: ">=4"},
                        {binary: true, worker_thresh: 5, included_severity: "all"},
                        {binary: true, worker_thresh: 5, included_severity: "<=2"},
                        {binary: true, worker_thresh: 5, included_severity: ">=3"},
                        {binary: true, worker_thresh: 5, included_severity: "<=3"},
                        {binary: true, worker_thresh: 5, included_severity: ">=4"},
                        {binary: false, worker_thresh: 1, included_severity: "all"},
                        {binary: false, worker_thresh: 1, included_severity: "<=2"},
                        {binary: false, worker_thresh: 1, included_severity: ">=3"},
                        {binary: false, worker_thresh: 1, included_severity: "<=3"},
                        {binary: false, worker_thresh: 1, included_severity: ">=4"},
                        {binary: false, worker_thresh: 3, included_severity: "all"},
                        {binary: false, worker_thresh: 3, included_severity: "<=2"},
                        {binary: false, worker_thresh: 3, included_severity: ">=3"},
                        {binary: false, worker_thresh: 3, included_severity: "<=3"},
                        {binary: false, worker_thresh: 3, included_severity: ">=4"},
                        {binary: false, worker_thresh: 5, included_severity: "all"},
                        {binary: false, worker_thresh: 5, included_severity: "<=2"},
                        {binary: false, worker_thresh: 5, included_severity: ">=3"},
                        {binary: false, worker_thresh: 5, included_severity: "<=3"},
                        {binary: false, worker_thresh: 5, included_severity: ">=4"}
                    ];
                console.log(fiveTurkerData);

                let offset = optsArrayOneTurker.length + optsArrayFiveTurkers.length;
                for (let k = 0; k < optsArrayFiveTurkers.length; k++) {

                    let output = setupAccuracy(fiveTurkerData, 5, optsArrayFiveTurkers[k]);
                    accuracyOutputArray[k + offset] = calculateAccuracy(output);
                    console.log("" + (k + 1) + " down, " + (optsArrayFiveTurkers.length - k - 1) + " to go!");
                }

                // export CSV
                exportCSVFile(accuracyOutputArray, "accuracies-turker");
                $("#all-accuracy-result").html("Success! Enjoy your CSV!");
            });
        });
    });
};

let allIndividualTurkerButton = document.getElementById('allIndividualTurkerAccuracy');
allIndividualTurkerButton.onclick = function() {

    $.getJSON("/accuracyForEachTurker", function (oneTurkerData) {
        console.log(oneTurkerData);
        let accuracyOutputArray = [];
        let optsArrayOneTurker =
            [
                {binary: true, included_severity: "all"},
                {binary: true, included_severity: "<=2"},
                {binary: true, included_severity: ">=3"},
                {binary: true, included_severity: "<=3"},
                {binary: true, included_severity: ">=4"},
                {binary: false, included_severity: "all"},
                {binary: false, included_severity: "<=2"},
                {binary: false, included_severity: ">=3"},
                {binary: false, included_severity: "<=3"},
                {binary: false, included_severity: ">=4"}
            ];
        // console.log(oneTurkerData);

        let outputIndex = 0;
        for (let i = 0; i < oneTurkerData[0].length; i++) {
            for (let j = 0; j < optsArrayOneTurker.length; j++) {

                let output = setupAccuracy(oneTurkerData[0][i], 1, optsArrayOneTurker[j]);
                accuracyOutputArray[outputIndex] = calculateAccuracy(output);
                outputIndex += 1;
                console.log("" + (j + 1) + " down, " + (optsArrayOneTurker.length - j - 1) + " to go!");
            }
        }

        // export CSV
        exportCSVFile(accuracyOutputArray, "accuracies-turker-all-individuals");
        $("#all-accuracy-result").html("Success! Enjoy your CSV!");
    });
};

let testDifferentThresholdsButton = document.getElementById('testDifferentThresholds');
testDifferentThresholdsButton.onclick = function() {

	let floatBuffer = 0.000001;
	function runClusteringForThreshold(firstThreshold, thresholdIncrement, index, maxThreshold) {
		let currentThreshold = firstThreshold + thresholdIncrement * index;

		$.getJSON("/accuracyForEachTurker?threshold=" + currentThreshold, function (oneTurkerData) {
			// console.log(oneTurkerData);
			let accuracyOutputArray = [];
			let optsArrayOneTurker = {binary: true, included_severity: "all"};

			// data from the GET should be an array of length 5, b/c 5 turkers completed each condition
			let outputIndex = 0;
			for (let i = 0; i < oneTurkerData[0].length; i++) {

				let output = setupAccuracy(oneTurkerData[0][i], 1, optsArrayOneTurker);
				accuracyOutputArray[outputIndex] = calculateAccuracy(output);
				outputIndex += 1;
			}

			// export CSV
			exportCSVFile(accuracyOutputArray, "accuracies-turker-" + currentThreshold.toFixed(3));

			// recursive call to run clustering on a new threshold
			if (currentThreshold + thresholdIncrement < maxThreshold + floatBuffer) {
				console.log("" + (index + 1) + " down, " + ((maxThreshold / thresholdIncrement) - index) + " to go!");
				runClusteringForThreshold(firstThreshold, thresholdIncrement, index + 1, maxThreshold);
			} else {
				console.log("Finished all " + (index + 1) + "!");
				$("#all-accuracy-result").html("Success! Enjoy your CSVs!");
			}
		});
	}

	runClusteringForThreshold(0.0, 0.001, 0, 0.05);
};

let testDifferentThresholdsButton2 = document.getElementById('testDifferentThresholds2');
testDifferentThresholdsButton2.onclick = function() {

	let floatBuffer = 0.000001;
	function runClusteringForThreshold(firstThreshold, thresholdIncrement, index, maxThreshold) {
		let currentThreshold = firstThreshold + thresholdIncrement * index;

		$.getJSON("/accuracyDataWithSinglePersonClust/turker/5?threshold=" + currentThreshold, function (clusteredTurkerData) {
			// console.log(clusteredTurkerData);
			let accuracyOutputArray = [];
			let optsArrayClusteredTurkers =
				[
					{binary: true, worker_thresh: 1, included_severity: "all"},
					{binary: true, worker_thresh: 3, included_severity: "all"},
					{binary: true, worker_thresh: 5, included_severity: "all"},
					{binary: false, worker_thresh: 1, included_severity: "all"},
					{binary: false, worker_thresh: 3, included_severity: "all"},
					{binary: false, worker_thresh: 5, included_severity: "all"}
				];

			let outputIndex = 0;
            for (let i = 0; i < optsArrayClusteredTurkers.length; i++) {

                let output = setupAccuracy(clusteredTurkerData, 5, optsArrayClusteredTurkers[i]);
                accuracyOutputArray[outputIndex] = calculateAccuracy(output);
                outputIndex += 1;
            }

			// export CSV
			exportCSVFile(accuracyOutputArray, "accuracies-turker-5-" + currentThreshold.toFixed(5));

			// recursive call to run clustering on a new threshold
			if (currentThreshold + thresholdIncrement < maxThreshold + floatBuffer) {
				console.log("" + (index + 1) + " down, " + ((maxThreshold / thresholdIncrement) - index) + " to go!");
				runClusteringForThreshold(firstThreshold, thresholdIncrement, index + 1, maxThreshold);
			} else {
				console.log("Finished all " + (index + 1) + "!");
				$("#all-accuracy-result").html("Success! Enjoy your CSVs!");
			}
		});
	}

	runClusteringForThreshold(0.0, 0.001, 0, 0.05);
};
