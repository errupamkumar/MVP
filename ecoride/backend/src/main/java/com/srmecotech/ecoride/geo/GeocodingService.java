package com.srmecotech.ecoride.geo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srmecotech.ecoride.common.config.AppProperties;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Place search ("Where to?") and reverse geocoding ("Pickup: Baner Road, Baner").
 * Curated city places answer instantly and offline; free text and street addresses go to
 * Nominatim (OpenStreetMap), limited to the service area, one request per second as its
 * usage policy asks, and cached.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final Duration SEARCH_TTL = Duration.ofDays(7);
    private static final Duration REVERSE_TTL = Duration.ofDays(30);
    private static final long MIN_SPACING_MS = 1100;
    private static final int MAX_RESULTS = 10;

    private final AppProperties properties;
    private final SettingsService settings;
    private final GeoCache cache;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient client;
    private final ReentrantLock nominatimLock = new ReentrantLock();
    private volatile long lastCallAt;
    private volatile Instant skipUntil = Instant.EPOCH;

    public GeocodingService(AppProperties properties, SettingsService settings, GeoCache cache, JdbcTemplate jdbc,
                            ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.settings = settings;
        this.cache = cache;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        AppProperties.Geocoding config = properties.geocoding();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.timeoutMs())).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(config.timeoutMs()));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(config.nominatimUrl())
                .defaultHeader("User-Agent", config.userAgent())
                .defaultHeader("Accept-Language", "en")
                .build();
    }

    public List<PlaceResult> search(String query, GeoPoint near) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return suggestions(near);
        }
        List<PlaceResult> results = new ArrayList<>(curated(q, near));
        if (q.length() >= 3 && results.size() < 6 && properties.geocoding().online()) {
            for (PlaceResult online : nominatimSearch(q, near)) {
                boolean duplicate = results.stream().anyMatch(r -> r.name().equalsIgnoreCase(online.name())
                        && GeoMath.distanceM(r.lat(), r.lng(), online.lat(), online.lng()) < 300);
                if (!duplicate) {
                    results.add(online);
                }
            }
        }
        String lower = q.toLowerCase(Locale.ROOT);
        results.sort(Comparator
                .comparing((PlaceResult r) -> !r.name().toLowerCase(Locale.ROOT).startsWith(lower))
                .thenComparing(r -> !"PLACE".equals(r.source()))
                .thenComparing(r -> r.distanceM() == null ? Integer.MAX_VALUE : r.distanceM()));
        return results.stream().limit(MAX_RESULTS).toList();
    }

    /** Popular destinations, nearest first, for an empty search box. */
    public List<PlaceResult> suggestions(GeoPoint near) {
        List<PlaceResult> all = jdbc.query("SELECT name, address, category, lat, lng FROM place",
                (rs, i) -> place(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5), near));
        Set<String> popular = Set.of("AIRPORT", "STATION", "MALL", "WORK");
        return all.stream()
                .sorted(Comparator.comparing((PlaceResult p) -> !popular.contains(p.category()))
                        .thenComparing(p -> p.distanceM() == null ? Integer.MAX_VALUE : p.distanceM()))
                .limit(8)
                .toList();
    }

    public PlaceResult reverse(GeoPoint point) {
        String key = "rev:%.4f,%.4f".formatted(point.lat(), point.lng());
        if (properties.geocoding().online()) {
            String cached = cache.get(key, REVERSE_TTL).orElse(null);
            if (cached != null) {
                String[] parts = cached.split("\\|", 2);
                return new PlaceResult(parts[0], parts.length > 1 ? parts[1] : parts[0], point.lat(), point.lng(),
                        "OSM", null, "ADDRESS");
            }
            JsonNode json = nominatim("/reverse?format=jsonv2&zoom=18&addressdetails=1&lat={lat}&lon={lng}",
                    point.lat(), point.lng());
            if (json != null && json.has("display_name")) {
                String name = displayName(json);
                String address = shortAddress(json);
                cache.put(key, name + "|" + address);
                return new PlaceResult(name, address, point.lat(), point.lng(), "OSM", null, "ADDRESS");
            }
        }
        return nearestPlaceLabel(point);
    }

    private PlaceResult nearestPlaceLabel(GeoPoint point) {
        List<PlaceResult> places = jdbc.query("SELECT name, address, category, lat, lng FROM place",
                (rs, i) -> place(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5), point));
        return places.stream()
                .min(Comparator.comparing(PlaceResult::distanceM))
                .filter(p -> p.distanceM() != null && p.distanceM() < 2500)
                .map(p -> new PlaceResult(p.distanceM() < 120 ? p.name() : "Near " + p.name(), p.address(),
                        point.lat(), point.lng(), "PLACE", null, "ADDRESS"))
                .orElseGet(() -> new PlaceResult("Pinned location", "%.5f, %.5f".formatted(point.lat(), point.lng()),
                        point.lat(), point.lng(), "PIN", null, "ADDRESS"));
    }

    private List<PlaceResult> curated(String q, GeoPoint near) {
        String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
        return jdbc.query("""
                        SELECT name, address, category, lat, lng FROM place
                        WHERE LOWER(name) LIKE ? OR LOWER(address) LIKE ? LIMIT 12
                        """,
                (rs, i) -> place(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getDouble(5), near),
                like, like);
    }

    private List<PlaceResult> nominatimSearch(String q, GeoPoint near) {
        String key = "search:" + q.toLowerCase(Locale.ROOT);
        try {
            String cached = cache.get(key, SEARCH_TTL).orElse(null);
            List<CachedPlace> found;
            if (cached != null) {
                found = objectMapper.readValue(cached, new TypeReference<>() {
                });
            } else {
                double radiusM = settings.getInt(Setting.SERVICE_AREA_RADIUS_KM) * 1000d;
                double lat = properties.serviceCenterLat();
                double lng = properties.serviceCenterLng();
                String viewbox = String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f",
                        lng - GeoMath.lngDelta(radiusM, lat), lat + GeoMath.latDelta(radiusM),
                        lng + GeoMath.lngDelta(radiusM, lat), lat - GeoMath.latDelta(radiusM));
                JsonNode json = nominatim("/search?format=jsonv2&limit=6&addressdetails=1&countrycodes=in&bounded=1&viewbox={vb}&q={q}",
                        viewbox, q);
                if (json == null || !json.isArray()) {
                    return List.of();
                }
                found = new ArrayList<>();
                for (JsonNode hit : json) {
                    found.add(new CachedPlace(displayName(hit), shortAddress(hit), hit.path("lat").asDouble(),
                            hit.path("lon").asDouble()));
                }
                cache.put(key, objectMapper.writeValueAsString(found));
            }
            return found.stream().map(c -> place(c.name(), c.address(), "ADDRESS", c.lat(), c.lng(), near))
                    .map(p -> new PlaceResult(p.name(), p.address(), p.lat(), p.lng(), "OSM", p.distanceM(), p.category()))
                    .toList();
        } catch (Exception ex) {
            log.warn("Place search for '{}' failed: {}", q, ex.getMessage());
            return List.of();
        }
    }

    /** One request per second (Nominatim usage policy); a caller that would wait too long gets nothing. */
    private JsonNode nominatim(String uri, Object... vars) {
        if (clock.instant().isBefore(skipUntil)) {
            return null;
        }
        try {
            if (!nominatimLock.tryLock(2500, TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            long wait = lastCallAt + MIN_SPACING_MS - System.currentTimeMillis();
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastCallAt = System.currentTimeMillis();
            String body = client.get().uri(uri, vars).retrieve().body(String.class);
            return objectMapper.readTree(body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            skipUntil = clock.instant().plusSeconds(30);
            log.warn("Nominatim unavailable ({}); using curated places only for 30 s", ex.getMessage());
            return null;
        } finally {
            nominatimLock.unlock();
        }
    }

    private static String displayName(JsonNode hit) {
        String name = hit.path("name").asText("");
        if (!name.isBlank()) {
            return name;
        }
        JsonNode a = hit.path("address");
        for (String field : List.of("road", "neighbourhood", "suburb", "quarter", "village", "town", "city_district")) {
            String value = a.path(field).asText("");
            if (!value.isBlank()) {
                String house = a.path("house_number").asText("");
                return house.isBlank() || !field.equals("road") ? value : house + ", " + value;
            }
        }
        String display = hit.path("display_name").asText("Pinned location");
        return display.split(",")[0].trim();
    }

    private static String shortAddress(JsonNode hit) {
        JsonNode a = hit.path("address");
        if (a.isObject()) {
            Set<String> parts = new LinkedHashSet<>();
            for (String field : List.of("road", "neighbourhood", "suburb", "city_district", "city", "town", "village", "county")) {
                String value = a.path(field).asText("");
                if (!value.isBlank()) {
                    parts.add(value);
                }
            }
            if (!parts.isEmpty()) {
                return parts.stream().limit(4).collect(Collectors.joining(", "));
            }
        }
        String display = hit.path("display_name").asText("");
        return Arrays.stream(display.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("India") && !s.matches("\\d{6}"))
                .limit(4)
                .collect(Collectors.joining(", "));
    }

    private static PlaceResult place(String name, String address, String category, double lat, double lng, GeoPoint near) {
        Integer distance = near == null ? null : (int) Math.round(GeoMath.distanceM(near.lat(), near.lng(), lat, lng));
        return new PlaceResult(name, address, lat, lng, "PLACE", distance, category);
    }

    private record CachedPlace(String name, String address, double lat, double lng) {
    }

    /**
     * @param source PLACE (curated), OSM (OpenStreetMap search) or PIN (coordinates only)
     */
    public record PlaceResult(String name, String address, double lat, double lng, String source, Integer distanceM,
                              String category) {
        public PlaceResult {
            Objects.requireNonNull(name);
        }
    }
}
