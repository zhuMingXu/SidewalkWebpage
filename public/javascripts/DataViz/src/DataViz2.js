function DataViz2(_, $, c3, turf, version) {
    var self = {};
    var severityList = [1, 2, 3, 4, 5];
    self.markerLayer = null;
    self.curbRampLayers = [];
    self.missingCurbRampLayers = [];
    self.obstacleLayers = [];
    self.surfaceProblemLayers = [];
    self.cantSeeSidewalkLayers = [];
    self.noSidewalkLayers = [];
    self.otherLayers = [];
    self.mapLoaded = false;
    self.graphsLoaded = false;

    var neighborhoodPolygonLayer;

    // Different severities
    /*
    for (i = 0; i < 5; i++) {
        self.curbRampLayers[i] = [];
        self.missingCurbRampLayers[i] = [];
        self.obstacleLayers[i] = [];
        self.surfaceProblemLayers[i] = [];
        self.cantSeeSidewalkLayers[i] = [];
        self.noSidewalkLayers[i] = [];
        self.otherLayers[i] = [];
    }*/

    self.labelLayer = null;
    self.labelDataLayer = [];

    self.allLayers = {
        "CurbRamp": self.curbRampLayers, "NoCurbRamp": self.missingCurbRampLayers, "Obstacle": self.obstacleLayers,
        "SurfaceProblem": self.surfaceProblemLayers, "Occlusion": self.cantSeeSidewalkLayers,
        "NoSidewalk": self.noSidewalkLayers, "Other": self.otherLayers
    };

    self.auditedStreetLayer = null;

    L.mapbox.accessToken = 'pk.eyJ1Ijoia290YXJvaGFyYSIsImEiOiJDdmJnOW1FIn0.kJV65G6eNXs4ATjWCtkEmA';

    // var tileUrl = "https://a.tiles.mapbox.com/v4/kotarohara.mmoldjeh/page.html?access_token=" + L.mapbox.accessToken + "#13/38.8998/-77.0638";
    var tileUrl = "https:\/\/a.tiles.mapbox.com\/v4\/kotarohara.8e0c6890\/{z}\/{x}\/{y}.png?access_token=" + L.mapbox.accessToken;
    L.tileLayer(tileUrl, {
        attribution: '<a href="http://www.mapbox.com/about/maps/" target="_blank">Terms &amp; Feedback</a>'
    });

    // Construct a bounding box for these maps that the user cannot move out of
    // https://www.mapbox.com/mapbox.js/example/v1.0.0/maxbounds/
    var southWest = L.latLng(38.761, -77.262);
    var northEast = L.latLng(39.060, -76.830);
    var bounds = L.latLngBounds(southWest, northEast);
    var defaultZoomLevel = 12;

    // Create the map
    var map = L.mapbox.map('viz-map', "kotarohara.8e0c6890", {
        // set that bounding box as maxBounds to restrict moving the map
        // see full maxBounds documentation:
        // http://leafletjs.com/reference.html#map-maxbounds
        maxBounds: bounds,
        maxZoom: 19,
        minZoom: 12
    }).fitBounds(bounds).setView([38.892, -77.038], defaultZoomLevel);

    // Disable scroll zoom
    if (map.scrollWheelZoom) {
        map.scrollWheelZoom.disable();
    }

    // Enable zooming with two fingers
    map.touchZoom.enable();

    var currentZoomLevel = defaultZoomLevel;

    function requestAndApplyDataForZoomLevel(zoomLevelToRequest) {
        if (self.labelDataLayer[zoomLevelToRequest] === undefined) {
            $.getJSON("/dataviz/labels/zoom/" + zoomLevelToRequest, function (data) {
                self.labelDataLayer.push(data);
                applyLayers(data, true);
            });
        } else {
            applyLayers(self.labelDataLayer[zoomLevelToRequest], true);
        }
    }

    if (version === 2 || version === 3) {

        // Zoom detection
        map.on("zoomstart", function (e) {
            currentZoomLevel = map.getZoom();
        });

        var zoomLevelToRequest;
        map.on("zoom", function (e) {

            var zoom = currentZoomLevel;
            if (map.getZoom() > currentZoomLevel) {
                // Zooming in
                zoom += 1;
            } else {
                // Zooming out
                zoom -= 1;
            }
            // Call server to get the certain zoom level's data
            zoomLevelToRequest = zoom - defaultZoomLevel;

            if (version === 2 || (version === 3 && zoomLevelToRequest === 0)) {
                console.log("Requesting server for zoom level " + zoomLevelToRequest);
                requestAndApplyDataForZoomLevel(zoomLevelToRequest)
            }
        });

        if (version === 3) {
            function onZoomAndPanEnd() {

                if (zoomLevelToRequest > 0) {
                    var lat1 = map.getBounds().getNorthWest().lat,
                        lng1 = map.getBounds().getNorthWest().lng,
                        lat2 = map.getBounds().getSouthEast().lat,
                        lng2 = map.getBounds().getSouthEast().lng;


                    var queryString = "lat1=" + lat1 + "&lng1=" + lng1 + "&lat2=" + lat2 + "&lng2=" + lng2 +
                        "&zoomLevel=" + zoomLevelToRequest;
                    var url = "/dataviz/labels/box?" + queryString;
                    //console.log("Requesting version 3 for " + url);

                    $.getJSON(url, function (data) {
                        applyLayers(data, true);
                    });
                }
            }
            map.on('zoomend', onZoomAndPanEnd);
            map.on('dragend', onZoomAndPanEnd);
        }
    }

    var popup = L.popup().setContent('<p>Hello world!<br />This is a nice popup.</p>');

    // Initialize the map
    /**
     * This function adds a semi-transparent white polygon on top of a map
     */
    function initializeOverlayPolygon(map) {
        var overlayPolygon = {
            "type": "FeatureCollection",
            "features": [{
                "type": "Feature", "geometry": {
                    "type": "Polygon", "coordinates": [
                        [[-75, 36], [-75, 40], [-80, 40], [-80, 36], [-75, 36]]
                    ]
                }
            }]
        };
        var layer = L.geoJson(overlayPolygon);
        layer.setStyle({color: "#ccc", fillColor: "#ccc"});
        layer.addTo(map);
    }

    /**
     * render points
     */
    function initializeNeighborhoodPolygons(map) {
        var neighborhoodPolygonStyle = {
                color: '#888',
                weight: 1,
                opacity: 0.25,
                fillColor: "#ccc",
                fillOpacity: 0.1
            },
            layers = [],
            currentLayer;

        function onEachNeighborhoodFeature(feature, layer) {

            var regionId = feature.properties.region_id,
                url = "/audit/region/" + regionId,
                popupContent = "Do you want to explore this area to find accessibility issues? " +
                    "<a href='" + url + "' class='region-selection-trigger' regionId='" + regionId + "'>Sure!</a>";
            layer.bindPopup(popupContent);
            layers.push(layer);

            layer.on('mouseover', function (e) {
                this.setStyle({color: "red", fillColor: "red"});

            });
            layer.on('mouseout', function (e) {
                for (var i = layers.length - 1; i >= 0; i--) {
                    if (currentLayer !== layers[i])
                        layers[i].setStyle(neighborhoodPolygonStyle);
                }
                //this.setStyle(neighborhoodPolygonStyle);
            });
            layer.on('click', function (e) {
                currentLayer = this;
            });
        }

        $.getJSON("/neighborhoods", function (data) {
            neighborhoodPolygonLayer = L.geoJson(data, {
                style: function (feature) {
                    return $.extend(true, {}, neighborhoodPolygonStyle);
                },
                onEachFeature: onEachNeighborhoodFeature
            })
                .addTo(map);
        });
    }

    /**
     * This function queries the streets that the user audited and visualize them as segments on the map.
     */
    function initializeAuditedStreets(map) {
        var distanceAudited = 0,  // Distance audited in km
            streetLinestringStyle = {
                color: "black",
                weight: 3,
                opacity: 0.75
            };

        function onEachStreetFeature(feature, layer) {
            if (feature.properties && feature.properties.type) {
                layer.bindPopup(feature.properties.type);
            }
            layer.on({
                'add': function () {
                    layer.bringToBack()
                }
            })
        }

        $.getJSON("/contribution/streets/all", function (data) {

            // Render audited street segments
            self.auditedStreetLayer = L.geoJson(data, {
                pointToLayer: L.mapbox.marker.style,
                style: function (feature) {
                    var style = $.extend(true, {}, streetLinestringStyle);
                    var randomInt = Math.floor(Math.random() * 5);
                    style.color = "#000";
                    style["stroke-width"] = 3;
                    style.opacity = 0.75;
                    style.weight = 3;

                    return style;
                },
                onEachFeature: onEachStreetFeature
            })
                .addTo(map);

            // Calculate total distance audited in (km)
            for (var i = data.features.length - 1; i >= 0; i--) {
                distanceAudited += turf.lineDistance(data.features[i]);
            }
            // document.getElementById("td-total-distance-audited").innerHTML = distanceAudited.toPrecision(2) + " km";
        });
    }

    function initializeSubmittedLabels() {

        // Create legend
        document.getElementById("map-legend-curb-ramp").innerHTML = "<svg width='20' height='20'><circle r='6' cx='10' cy='10' fill='" + colorMapping['CurbRamp'].fillStyle + "'></svg>";
        document.getElementById("map-legend-no-curb-ramp").innerHTML = "<svg width='20' height='20'><circle r='6' cx='10' cy='10' fill='" + colorMapping['NoCurbRamp'].fillStyle + "'></svg>";
        document.getElementById("map-legend-obstacle").innerHTML = "<svg width='20' height='20'><circle r='6' cx='10' cy='10' fill='" + colorMapping['Obstacle'].fillStyle + "'></svg>";
        document.getElementById("map-legend-surface-problem").innerHTML = "<svg width='20' height='20'><circle r='6' cx='10' cy='10' fill='" + colorMapping['SurfaceProblem'].fillStyle + "'></svg>";
        document.getElementById("map-legend-other").innerHTML = "<svg width='20' height='20'><circle r='6' cx='10' cy='10' fill='" + colorMapping['Other'].fillStyle + "' stroke='" + colorMapping['Other'].strokeStyle + "'></svg>";
        document.getElementById("map-legend-occlusion").innerHTML = "<svg width='20' height='20'><circle r='6' cx='10' cy='10' fill='" + colorMapping['Other'].fillStyle + "' stroke='" + colorMapping['Occlusion'].strokeStyle + "'></svg>";
        document.getElementById("map-legend-nosidewalk").innerHTML = "<svg width='20' height='20'><circle r='6' cx='10' cy='10' fill='" + colorMapping['Other'].fillStyle + "' stroke='" + colorMapping['NoSidewalk'].strokeStyle + "'></svg>";
        //document.getElementById("map-legend-audited-street").innerHTML = "<svg width='20' height='20'><path stroke='black' stroke-width='3' d='M 2 10 L 18 10 z'></svg>";

        var url;
        if (version === 1) {
            url = "/dataviz/labels/all"
        } else {
            url = "/dataviz/labels/zoom/0"
        }
        $.getJSON(url, function (data) {
            if (version === 3) {
                if (self.labelDataLayer[0] === undefined) {
                    self.labelDataLayer.push(data);
                } else {
                    //Unlikely
                    console.log("Zoom level 0 already available");
                }
            }
            applyLayers(data, false);
        });
    }


    function onEachLabelFeature(feature, layer) {
        layer.on('click', function () {
            self.adminGSVLabelView.showLabel(feature.properties.label_id);
        });
        layer.on({
            'mouseover': function () {
                layer.setRadius(15);
            },
            'mouseout': function () {
                layer.setRadius(5);
            }
        });
    }

    var colorMapping = util.misc.getLabelColors(),
        geojsonMarkerOptions = {
            radius: 5,
            fillColor: "#ff7800",
            color: "#ffffff",
            weight: 1,
            opacity: 0.5,
            fillOpacity: 0.5,
            "stroke-width": 1
        };

    function createLayer(data) {
        return L.geoJson(data, {
            pointToLayer: function (feature, latlng) {
                var style = $.extend(true, {}, geojsonMarkerOptions);
                style.fillColor = colorMapping[feature.properties.label_type].fillStyle;
                style.color = colorMapping[feature.properties.label_type].strokeStyle;
                return L.circleMarker(latlng, style);
            }//, onEachFeature: onEachLabelFeature
        })
    }

    function applyLayers(data, reapply) {

        // Remove previous labels and apply a new layer if reapply=true
        if (reapply) map.removeLayer(self.labelLayer);
        self.labelLayer = createLayer({"type": "FeatureCollection", "features": data.features});
        self.labelLayer.addTo(map);
    }

    function initializeAllLayers(data) {
        // TODO: This might be taking time because it is going through each label iteratively
        for (i = 0; i < data.features.length; i++) {
            var labelType = data.features[i].properties.label_type;

            if (data.features[i].properties.severity === 1) {
                self.allLayers[labelType][0].push(data.features[i]);
            } else if (data.features[i].properties.severity === 2) {
                self.allLayers[labelType][1].push(data.features[i]);
            } else if (data.features[i].properties.severity === 3) {
                self.allLayers[labelType][2].push(data.features[i]);
            } else if (data.features[i].properties.severity === 4) {
                self.allLayers[labelType][3].push(data.features[i]);
            } else if (data.features[i].properties.severity === 5) {
                self.allLayers[labelType][4].push(data.features[i]);
            }
        }

        Object.keys(self.allLayers).forEach(function (key) {
            for (i = 0; i < self.allLayers[key].length; i++) {
                self.allLayers[key][i] = createLayer({"type": "FeatureCollection", "features": self.allLayers[key][i]});

                if (reapply) map.removeLayer(self.allLayers[key][i]);
                self.allLayers[key][i].addTo(map);
            }
        });
    }

    function clearMap() {
        map.removeLayer(self.markerLayer);
    }

    function clearAuditedStreetLayer() {
        map.removeLayer(self.auditedStreetLayer);
    }

    function redrawAuditedStreetLayer() {
        initializeAuditedStreets(map);
    }

    function redrawLabels() {
        initializeSubmittedLabels(map);
    }

    function toggleLayers(label, checkboxId, sliderId) {
        if (document.getElementById(checkboxId).checked) {
            if(checkboxId === "occlusion" || checkboxId === "nosidewalk"){
                for (i = 0; i < self.allLayers[label].length; i++) {
                    if (!map.hasLayer(self.allLayers[label][i])) {
                        map.addLayer(self.allLayers[label][i]);
                    }
                }
            }
            else {
                for (i = 0; i < self.allLayers[label].length; i++) {
                    if (!map.hasLayer(self.allLayers[label][i])
                        && ($(sliderId).slider("option", "value") === i ||
                        $(sliderId).slider("option", "value") === 5 )) {
                        map.addLayer(self.allLayers[label][i]);
                    } else if ($(sliderId).slider("option", "value") !== 5
                        && $(sliderId).slider("option", "value") !== i) {
                        map.removeLayer(self.allLayers[label][i]);
                    }
                }
            }
        } else {
            for (i = 0; i < self.allLayers[label].length; i++) {
                if (map.hasLayer(self.allLayers[label][i])) {
                    map.removeLayer(self.allLayers[label][i]);
                }
            }
        }
    }

    function toggleAuditedStreetLayer() {
        if (document.getElementById('auditedstreet').checked) {
            map.addLayer(self.auditedStreetLayer);
        } else {
            map.removeLayer(self.auditedStreetLayer);
        }
    }

    function initializeAdminGSVLabelView() {
        self.adminGSVLabelView = AdminGSVLabel();
    }

    function initializeLabelTable() {
        $('.labelView').click(function (e) {
            e.preventDefault();
            self.adminGSVLabelView.showLabel($(this).data('labelId'));
        });
    }

    initializeOverlayPolygon(map);
    initializeNeighborhoodPolygons(map);
    //initializeAuditedStreets(map);
    initializeSubmittedLabels();
    setTimeout(function () {
        map.invalidateSize(false);
    }, 1);

    // initializeLabelTable();
    // initializeAdminGSVLabelView();

    self.clearMap = clearMap;
    self.redrawLabels = redrawLabels;
    self.clearAuditedStreetLayer = clearAuditedStreetLayer;
    self.redrawAuditedStreetLayer = redrawAuditedStreetLayer;
    self.toggleLayers = toggleLayers;
    self.toggleAuditedStreetLayer = toggleAuditedStreetLayer;

    return self;
}
