/**
 * Stores string descriptions for each examples and counterexample image for each label type that
 * appears on the validation interface.
 * @returns {StatusPopupDescriptions}
 * @constructor
 */
function StatusPopupDescriptions () {
    var self = this;

    function getCurbRampDescription (id) {
        switch (id) {
            case "example-image-1":
                return "This label is correctly placed at the bottom of a curb ramp";
            case "example-image-2":
                return "This label is correctly placed at the bottom of a curb ramp";
            case "example-image-3":
                return "This label is correctly placed at the bottom of a curb ramp";
            case "example-image-4":
                return "This label is correctly placed at the bottom of a curb ramp";
            case "counterexample-image-1":
                return "Sidewalk to driveway transitions are not curb ramps";
            case "counterexample-image-2":
                return "Sidewalk to driveway transitions are not curb ramps";
            case "counterexample-image-3":
                return "Driveways are not curb ramps";
            case "counterexample-image-4":
                return "Driveways are not curb ramps";
        }
    }

    function getMissingCurbRampDescription (id) {
        switch (id) {
            case "example-image-1":
                return "This intersection is lacking a curb ramp";
            case "example-image-2":
                return "This intersection is lacking a curb ramp";
            case "example-image-3":
                return "This intersection is lacking a curb ramp";
            case "example-image-4":
                return "This crosswalk indicates there should be a curb ramp here";
            case "counterexample-image-1":
                return "Residential walkways should not be labeled as a missing curb ramp";
            case "counterexample-image-2":
                return "This is not located on a pedestrian path";
            case "counterexample-image-3":
                return "This is not an intersection pedestrians should cross at";
            case "counterexample-image-4":
                return "This is not located on a pedestrian path";
        }
    }

    function getObstacleDescription (id) {
        switch (id) {
            case "example-image-1":
                return "These construction cones are block a pedestrian crosswalk";
            case "example-image-2":
                return "This tree blocks an entire sidewalk";
            case "example-image-3":
                return "This pole blocks the sidewalk and a curb ramp entrance";
            case "example-image-4":
                return "This fire hydrant blocks the sidewalk";
            case "counterexample-image-1":
                return "There is enough space to comfortably pass this pole";
            case "counterexample-image-2":
                return "This pole located between, but not on these curb ramps";
            case "counterexample-image-3":
                return "This tree is not on the pedestrian path";
            case "counterexample-image-4":
                return "There is enough space to comfortably pass this recycling can lid";
        }
    }

    function getSurfaceProblemDescription (id) {
        switch (id) {
            case "example-image-1":
                return "This sidewalk is not level";
            case "example-image-2":
                return "This sidewalk is not level";
            case "example-image-3":
                return "This sidewalk is cracked";
            case "example-image-4":
                return "There is a substantial amount of grass growing in the sidewalk cracks";
            case "counterexample-image-1":
                return "Surface problems on curb ramps should not be labeled as surface problems";
            case "counterexample-image-2":
                return "This gravel is not on the pedestrian path";
            case "counterexample-image-3":
                return "This grass is not on the pedestrian path";
            case "counterexample-image-4":
                return "Normal sidewalk tiling is not a surface problem";
        }
    }

    function getNoSidewalkDescription (id) {
        switch (id) {
            case "example-image-1":
                return "This is an abruptly ending sidewalk";
            case "example-image-2":
                return "This is an abruptly ending sidewalk";
            case "example-image-3":
                return "This is an abruptly ending sidewalk";
            case "example-image-4":
                return "This is an abruptly ending sidewalk";
            case "counterexample-image-1":
                return "Narrow sidewalks should not be labeled as missing sidewalks";
            case "counterexample-image-2":
                return "Driveways are not missing sidewalks";
            case "counterexample-image-3":
                return "Traffic medians are not missing sidewalks";
            case "counterexample-image-4":
                return "Alleyways and side streets should not be labeled as missing sidewalks";
        }
    }

    self.getCurbRampDescription = getCurbRampDescription;
    self.getMissingCurbRampDescription = getMissingCurbRampDescription;
    self.getObstacleDescription = getObstacleDescription;
    self.getSurfaceProblemDescription = getSurfaceProblemDescription;
    self.getNoSidewalkDescription = getNoSidewalkDescription;

    return this;
}