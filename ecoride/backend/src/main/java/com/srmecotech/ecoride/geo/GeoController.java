package com.srmecotech.ecoride.geo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Place search and reverse geocoding for every signed-in app. */
@RestController
@RequestMapping("/api/geo")
@Tag(name = "Maps")
public class GeoController {

    private final GeocodingService geocoding;
    private final RoutingService routing;

    public GeoController(GeocodingService geocoding, RoutingService routing) {
        this.geocoding = geocoding;
        this.routing = routing;
    }

    @GetMapping("/search")
    @Operation(summary = "Places matching the text, nearest first (popular places when the text is empty)")
    public List<GeocodingService.PlaceResult> search(@RequestParam(defaultValue = "") String q,
                                                     @RequestParam(required = false) Double lat,
                                                     @RequestParam(required = false) Double lng) {
        GeoPoint near = lat != null && lng != null ? new GeoPoint(lat, lng) : null;
        return geocoding.search(q, near);
    }

    @GetMapping("/reverse")
    @Operation(summary = "A readable label for a GPS position (street and area)")
    public GeocodingService.PlaceResult reverse(@RequestParam double lat, @RequestParam double lng) {
        return geocoding.reverse(new GeoPoint(lat, lng));
    }

    @GetMapping("/route")
    @Operation(summary = "Road route between two points (distance, time with traffic, polyline)")
    public RouteResult route(@RequestParam double fromLat, @RequestParam double fromLng,
                             @RequestParam double toLat, @RequestParam double toLng) {
        return routing.route(new GeoPoint(fromLat, fromLng), new GeoPoint(toLat, toLng));
    }
}
