// Takes a set of points and a set of street geometries. Fits labels to those streets, giving counts of how many labels
// of each label type are closest to each street. Streets are then also split up into smaller line segments, and the
// same counts are then tabulated for each of those segments.
function setupIRR(data) {
    // unpack different pieces of data
    var streetsData = data.streets;
    var labelsData = data.labels;

    // we are doing this for each route separately, but combining the results at the end
    var routes = [];
    for(var routeIndex = 0; routeIndex < routes.length; routeIndex++) {

        // street level
        for(var labIndex = 0; labIndex < labels.length; labIndex++) {
            // TODO get closest street to this label
            // http://turfjs.org/docs/#pointonline  (really read this documentation, this func has tons of useful output data)

            // TODO increment this street's count of labels (of this label type)

        }

        // segment level
        // TODO combine streets into a set of contiguous linestrings
        // http://turfjs.org/docs/#combine -- combines the different streets into a single MultiLineString
        // http://turfjs.org/docs/#lineintersect -- lets you know the points where two lines intersect

        var segDists = [5, 10]; // in meters
        for(var segDistIndex = 0; segDistIndex < segDists.length; segDistIndex++) {
            var segDist = segDists[routeIndex];

            // TODO split streets into a bunch of little segments based on segDist and length of each contiguous segment
            // http://turfjs.org/docs/#linechunk

            for(var labIndex = 0; labIndex < labels.length; labIndex++) {
                // TODO get closest segment to this label
                // http://turfjs.org/docs/#pointonline  (really read this documentation, this func has tons of useful output data)

                // TODO increment this segment's count of labels (of this label type)

            }
        }

        // TODO save the output into some object

    }
    
}

// TODO Takes the results of the IRR setup and outputs the CSVs on the client machine. Maybe all in a .tar or something?
function outputData() {
    
}

function IRR(data, turf) {
    console.log("Data received: ", data);
}