// Takes a set of points and a set of street geometries. Fits labels to those streets, giving counts of how many labels
// of each label type are closest to each street. Streets are then also split up into smaller line segments, and the
// same counts are then tabulated for each of those segments.
function setupIRR(data) {
    // unpack different pieces of data
    let streetsData = data.streets;
    let labelsData = data.labels;
    let routes = [...new Set(streetsData.features.map(street => street.properties.route_id))]; // gets unique set of routes
    let output = [];
    for(let i = 0; i < routes.length; i++) output[i] = {};

    for(let routeIndex = 0; routeIndex < routes.length; routeIndex++) {
        let currRoute = routes[routeIndex];
        let segs = streetsData.features.filter(street => street.properties.route_id === currRoute);
        let streetOutput = [];
        for(let i = 0; i < segs.length; i++) {
            streetOutput[i] =
                {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0,"Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0};
        }

        // street level
        for(let labIndex = 0; labIndex < labelsData.features.length; labIndex++) {
            // let currLabel = turf.point([labelsData[labIndex].lng, labelsData[labIndex].lat]);
            let currLabel = labelsData.features[labIndex];

            // get closest street to this label
            // http://turfjs.org/docs/#pointonline
            let segIndex;
            let minDist = Number.POSITIVE_INFINITY;
            for (let i = 0; i < segs.length; i++) {
                let closestPoint = turf.pointOnLine(segs[i], currLabel);
                if (closestPoint.properties.dist < minDist) {
                    segIndex = closestPoint.properties.index;
                }
            }

            // increment this street's count of labels (of this label type)
            streetOutput[segIndex][currLabel.properties.label_type] += 1;

        }
        output[routeIndex].street = streetOutput;
        console.log(output);


        // segment level
        // combine streets into a set of contiguous linestrings
        // http://turfjs.org/docs/#combine -- combines the different streets into a single MultiLineString
        // http://turfjs.org/docs/#lineintersect -- lets you know the points where two lines intersect
        let combinedStreets = turf.combine(streetsData);
        console.log(turf.combine(streetsData));


        let segDists = [0.005, 0.01]; // in meters
        for(let segDistIndex = 0; segDistIndex < segDists.length; segDistIndex++) {
            let segDist = segDists[segDistIndex];

            // split streets into a bunch of little segments based on segDist and length of each contiguous segment
            // TODO make sure that each contiguous segment is done separately
            // TODO pick actual distance for each contiguous segment separately so that all segs are approx equal
            // http://turfjs.org/docs/#linechunk
            let chunks = turf.lineChunk(combinedStreets, segDist);
            console.log(chunks);

            let segOutput = [];
            for(let i = 0; i < chunks.features.length; i++) {
                segOutput[i] =
                    {"CurbRamp": 0, "NoCurbRamp": 0, "NoSidewalk": 0,"Obstacle": 0, "Occlusion": 0, "SurfaceProblem": 0};
            }

            for(let labIndex = 0; labIndex < labelsData.features.length; labIndex++) {
                let currLabel = labelsData.features[labIndex];

                // get closest segment to this label
                // http://turfjs.org/docs/#pointonline
                let segIndex;
                let minDist = Number.POSITIVE_INFINITY;
                for (let i = 0; i < segs.length; i++) {
                    let closestPoint = turf.pointOnLine(segs[i], currLabel);
                    if (closestPoint.properties.dist < minDist) {
                        segIndex = closestPoint.properties.index;
                    }
                }

                // TODO increment this segment's count of labels (of this label type)
                segOutput[segIndex][currLabel.properties.label_type] += 1;

            }
            output[routeIndex][String(segDist * 1000) + "_meter"] = segOutput;
        }

        // TODO save the output into some object
        console.log(output);

    }
    
}

// TODO Takes the results of the IRR setup and outputs the CSVs on the client machine. Maybe all in a .tar or something?
function outputData() {
    
}

function IRR(data, turf) {
    console.log("Data received: ", data);
    setupIRR(data);
}