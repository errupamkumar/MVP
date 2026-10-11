package com.srmecotech.ecoride.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srmecotech.ecoride.common.config.AppProperties;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Road routes from OSRM (OpenStreetMap data; the public demo server needs no key), with a
 * straight-line estimate when the server is unreachable so booking never stops. Routes are
 * cached for a day; durations are free-flow, so they are scaled by TRAFFIC_FACTOR on the way out.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    private static final Duration CACHE_TTL = Duration.ofDays(1);
    /** After a failure, skip the server for a while instead of making every request wait for a timeout. */
    private static final Duration BACKOFF = Duration.ofSeconds(30);

    private final AppProperties.Routing config;
    private final SettingsService settings;
    private final GeoCache cache;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient client;
    private volatile Instant skipUntil = Instant.EPOCH;

    public RoutingService(AppProperties properties, SettingsService settings, GeoCache cache,
                          ObjectMapper objectMapper, Clock clock) {
        this.config = properties.routing();
        this.settings = settings;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.clock = clock;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.timeoutMs())).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(config.timeoutMs()));
        this.client = RestClient.builder().requestFactory(factory).baseUrl(config.osrmUrl()).build();
    }

    public RouteResult route(GeoPoint from, GeoPoint to) {
        double traffic = settings.getDouble(Setting.TRAFFIC_FACTOR);
        if (config.provider() == AppProperties.RoutingProvider.STRAIGHT) {
            return straight(from, to);
        }
        String key = "route:%.4f,%.4f;%.4f,%.4f".formatted(from.lat(), from.lng(), to.lat(), to.lng());
        RouteResult raw = cache.get(key, CACHE_TTL).map(this::parseCached).orElse(null);
        if (raw == null && clock.instant().isAfter(skipUntil)) {
            raw = fetchOsrm(from, to);
            if (raw != null) {
                cache.put(key, "%d|%d|%s".formatted(raw.distanceM(), raw.durationS(), raw.polyline()));
            }
        }
        if (raw == null) {
            return straight(from, to);
        }
        return new RouteResult(raw.distanceM(), (int) Math.round(raw.durationS() * traffic), raw.polyline(), "OSRM");
    }

    /** The fallback: straight line x road factor at city speed. */
    public RouteResult straight(GeoPoint from, GeoPoint to) {
        double roadFactor = settings.getDouble(Setting.ROAD_FACTOR);
        double speedMs = settings.getInt(Setting.CITY_SPEED_KMH) / 3.6;
        int distance = (int) Math.round(GeoMath.distanceM(from, to) * roadFactor);
        int duration = (int) Math.round(distance / speedMs);
        return new RouteResult(distance, duration, Polyline.encode(List.of(from, to)), "STRAIGHT");
    }

    private RouteResult fetchOsrm(GeoPoint from, GeoPoint to) {
        String coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.lng(), from.lat(), to.lng(), to.lat());
        try {
            String body = client.get()
                    .uri("/route/v1/driving/{coords}?overview=full&geometries=polyline&steps=false", coords)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            if (!"Ok".equals(json.path("code").asText())) {
                log.info("OSRM could not route {} -> {}: {}", from, to, json.path("code").asText());
                return null;
            }
            JsonNode route = json.path("routes").path(0);
            int distance = (int) Math.round(route.path("distance").asDouble());
            int duration = (int) Math.round(route.path("duration").asDouble());
            String geometry = route.path("geometry").asText();
            if (geometry.isEmpty()) {
                return null;
            }
            return new RouteResult(Math.max(distance, 1), Math.max(duration, 1), geometry, "OSRM");
        } catch (Exception ex) {
            skipUntil = clock.instant().plus(BACKOFF);
            log.warn("OSRM unavailable ({}); using straight-line estimates for {} s", ex.getMessage(), BACKOFF.toSeconds());
            return null;
        }
    }

    private RouteResult parseCached(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            return null;
        }
        return new RouteResult(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2], "OSRM");
    }
}
