package com.srmecotech.ecoride.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "ecoride")
public record AppProperties(
        @NotBlank String city,
        double serviceCenterLat,
        double serviceCenterLng,
        @Valid @NotNull Security security,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Routing routing,
        @Valid @NotNull Geocoding geocoding,
        @Valid @NotNull Dispatch dispatch,
        @Valid @NotNull Demo demo) {

    public record Security(@NotBlank String jwtSecret, @Positive long jwtTtlHours) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public enum RoutingProvider { OSRM, STRAIGHT }

    public record Routing(@NotNull RoutingProvider provider, @NotBlank String osrmUrl, @Positive int timeoutMs) {
    }

    public record Geocoding(boolean online, @NotBlank String nominatimUrl, @NotBlank String userAgent, @Positive int timeoutMs) {
    }

    /** @param enabled false in tests, which call DispatchService.sweep() themselves */
    public record Dispatch(boolean enabled, @Positive long intervalMs) {
    }

    /**
     * @param clockOffset shifts the server clock (e.g. PT9H) so a demo recorded at night shows daytime;
     *                    zero outside recordings
     */
    public record Demo(boolean simulationEnabled, @Positive long simulationIntervalMs, boolean resetEnabled,
                       @Positive int historyDays, java.time.Duration clockOffset) {
    }
}
