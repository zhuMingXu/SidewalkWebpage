/**
 * Todo. Separate the UI component and the logic component
 * @param tracker
 * @param uiZoomControl
 * @returns {{className: string}}
 * @constructor
 * @memberof svl
 */
function ZoomControl (canvas, mapService, tracker, uiZoomControl) {
    var self = { 'className' : 'ZoomControl' },
        properties = {
            maxZoomLevel: 3,
            minZoomLevel: 1
        },
        status = {
            disableZoomIn: false,
            disableZoomOut: false
        },
        lock = {
            disableZoomIn: false,
            disableZoomOut: false
        },
        blinkInterval;


    /**
     * Blink the zoom in and zoom-out buttons
     */
    function blink () {
        stopBlinking();
        blinkInterval = window.setInterval(function () {
            uiZoomControl.zoomIn.toggleClass("highlight-50");
            uiZoomControl.zoomOut.toggleClass("highlight-50");
        }, 500);
    }

    /**
     * Disables zooming in
     * @method
     * @returns {self}
     */
    function disableZoomIn () {
        if (!lock.disableZoomIn) {
            status.disableZoomIn = true;
            if (uiZoomControl) {
                uiZoomControl.zoomIn.css('opacity', 0.5);
            }
        }
        return this;
    }

    /**
     * Enable zoom out
     */
    function disableZoomOut () {
        if (!lock.disableZoomOut) {
            status.disableZoomOut = true;
            if (uiZoomControl) {
                uiZoomControl.zoomOut.css('opacity', 0.5);
            }
        }
        return this;
    }

    /**
     * Enable zoom in
     */
    function enableZoomIn () {
        if (!lock.disableZoomIn) {
            status.disableZoomIn = false;
            if (uiZoomControl) {
                uiZoomControl.zoomIn.css('opacity', 1);
            }
        }
        return this;
    }

    /**
     * Enable zoom out
     */
    function enableZoomOut () {
        if (!lock.disableZoomOut) {
            status.disableZoomOut = false;
            if (uiZoomControl) {
                uiZoomControl.zoomOut.css('opacity', 1);
            }
        }
        return this;
    }

    /**
     * Get lock
     * @param name
     * @returns {*}
     */
    function getLock (name) {
        if (name in lock) {
            return lock[name];
        } else {
            throw 'You cannot access a property "' + name + '".';
        }
    }

    /**
     * Get status
     * @param name
     * @returns {*}
     */
    function getStatus (name) {
        if (name in status) {
            return status[name];
        } else {
            throw 'You cannot access a property "' + name + '".';
        }
    }

    /** Get a property.*/
    function getProperty (name) {
        if (name in properties) {
            return properties[name];
        } else {
            throw 'You cannot access a property "' + name + '".';
        }
    }

    /** Lock zoom in */
    function lockDisableZoomIn () {
        lock.disableZoomIn = true;
        return this;
    }

    /** Lock zoom out */
    function lockDisableZoomOut () {
        lock.disableZoomOut = true;
        return this;
    }

    /**
     * This is a callback function for zoom-in button. This function increments a sv zoom level.
     */
    function _handleZoomInButtonClick () {
        if (tracker)  tracker.push('Click_ZoomIn');

        if (!status.disableZoomIn) {
            var pov = mapService.getPov();
            setZoom(pov.zoom + 1);
            canvas.clear();
            canvas.render2();
        }
    }

    /**
     * This is a callback function for zoom-out button. This function decrements a sv zoom level.
     */
    function _handleZoomOutButtonClick () {
        if (tracker) tracker.push('Click_ZoomOut');

        if (!status.disableZoomOut) {
            var pov = mapService.getPov();
            setZoom(pov.zoom - 1);
            canvas.clear();
            canvas.render2();
        }
    }

    /**
     * This method takes a (x, y) canvas point and zoom in to that point.
     * @param x canvaz x coordinate
     * @param y canvas y coordinate
     * @returns {*}
     */
    function pointZoomIn (x, y) {
        if (!status.disableZoomIn) {
            // Cancel drawing when zooming in or out.
            canvas.cancelDrawing();

            var currentPov = mapService.getPov(),
                currentZoomLevel = currentPov.zoom,
                width = svl.canvasWidth, height = svl.canvasHeight,
                minPitch, maxPitch,
                zoomFactor, deltaHeading, deltaPitch, pov = {};

            if (currentZoomLevel >= properties.maxZoomLevel) return;
            zoomFactor = currentZoomLevel; // This needs to be fixed as it wouldn't work above level 3.
            deltaHeading = (x - (width / 2)) / width * (90 / zoomFactor); // Ugh. Hard coding.
            deltaPitch = - (y - (height / 2)) / height * (70 / zoomFactor); // Ugh. Hard coding.
            pov.zoom = currentZoomLevel + 1;
            pov.heading = currentPov.heading + deltaHeading;
            pov.pitch = currentPov.pitch + deltaPitch;
            // Adjust the pitch angle.
            maxPitch = mapService.getMaxPitch();
            minPitch = mapService.getMinPitch();
            if (pov.pitch > maxPitch) {
                pov.pitch = maxPitch;
            } else if (pov.pitch < minPitch) {
                pov.pitch = minPitch;
            }
            // Adjust the pitch so it won't exceed max/min pitch.
            mapService.setPov(pov);
            return currentZoomLevel;
        }
    }

    /**
     * This method sets the zoom level of the Street View.
     */
    function setZoom (zoomLevelIn) {
        if (typeof zoomLevelIn !== "number") { return false; }

        // Cancel drawing when zooming in or out.
        if ('canvas' in svl) { canvas.cancelDrawing(); }

        // Set the zoom level and change the panorama properties.
        var zoomLevel = undefined;
        zoomLevelIn = parseInt(zoomLevelIn);
        if (zoomLevelIn < 1) {
            zoomLevel = 1;
        } else if (zoomLevelIn > properties.maxZoomLevel) {
            zoomLevel = properties.maxZoomLevel;
        } else {
            zoomLevel = zoomLevelIn;
        }
        mapService.setZoom(zoomLevel);
        return zoomLevel;
    }

    /**
     * Stop blinking the zoom-in and zoom-out buttons
     */
    function stopBlinking () {
        window.clearInterval(blinkInterval);
        if (uiZoomControl) {
            uiZoomControl.zoomIn.removeClass("highlight-50");
            uiZoomControl.zoomOut.removeClass("highlight-50");
        }
    }



    /**
     * This method sets the maximum zoom level.
     */
    function setMaxZoomLevel (zoomLevel) {
        properties.maxZoomLevel = zoomLevel;
        return this;
    }

    /** This method sets the minimum zoom level. */
    function setMinZoomLevel (zoomLevel) {
        properties.minZoomLevel = zoomLevel;
        return this;
    }

    /** Lock zoom in */
    function unlockDisableZoomIn () {
        lock.disableZoomIn = false;
        return this;
    }

    /** Lock zoom out */
    function unlockDisableZoomOut () {
        lock.disableZoomOut = false;
        return this;
    }

    /**
     * Change the opacity of zoom buttons
     * @returns {updateOpacity}
     */
    function updateOpacity () {
        var pov = mapService.getPov();

        if (pov && uiZoomControl) {
            var zoom = pov.zoom;
            // Change opacity
            if (zoom >= properties.maxZoomLevel) {
                uiZoomControl.zoomIn.css('opacity', 0.5);
                uiZoomControl.zoomOut.css('opacity', 1);
            } else if (zoom <= properties.minZoomLevel) {
                uiZoomControl.zoomIn.css('opacity', 1);
                uiZoomControl.zoomOut.css('opacity', 0.5);
            } else {
                uiZoomControl.zoomIn.css('opacity', 1);
                uiZoomControl.zoomOut.css('opacity', 1);
            }
        }

        // If zoom in and out are disabled, fade them out anyway.
        if (status.disableZoomIn) { uiZoomControl.zoomIn.css('opacity', 0.5); }
        if (status.disableZoomOut) { uiZoomControl.zoomOut.css('opacity', 0.5); }
        return this;
    }

    /** Zoom in */
    function zoomIn () {
        if (!status.disableZoomIn) {
            var pov = mapService.getPov();
            setZoom(pov.zoom + 1);
            canvas.clear().render2();
            return this;
        } else {
            return false;
        }
    }

    /** Zoom out */
    function zoomOut () {
        // This method is called from outside this class to zoom out from a GSV image.
        if (!status.disableZoomOut) {
            // ViewControl_ZoomOut
            var pov = mapService.getPov();
            setZoom(pov.zoom - 1);
            canvas.clear();
            canvas.render2();
            return this;
        } else {
            return false;
        }
    }

    self.blink = blink;
    self.disableZoomIn = disableZoomIn;
    self.disableZoomOut = disableZoomOut;
    self.enableZoomIn = enableZoomIn;
    self.enableZoomOut = enableZoomOut;
    self.getLock = getLock;
    self.getStatus = getStatus;
    self.getProperties = getProperty; // Todo. Change getProperties to getProperty.
    self.lockDisableZoomIn = lockDisableZoomIn;
    self.lockDisableZoomOut = lockDisableZoomOut;
    self.stopBlinking = stopBlinking;
    self.updateOpacity = updateOpacity;
    self.pointZoomIn = pointZoomIn;
    self.setMaxZoomLevel = setMaxZoomLevel;
    self.setMinZoomLevel = setMinZoomLevel;
    self.unlockDisableZoomIn = unlockDisableZoomIn;
    self.unlockDisableZoomOut = unlockDisableZoomOut;
    self.zoomIn = zoomIn;
    self.zoomOut = zoomOut;

    uiZoomControl.zoomIn.bind('click', _handleZoomInButtonClick);
    uiZoomControl.zoomOut.bind('click', _handleZoomOutButtonClick);
    return self;
}
