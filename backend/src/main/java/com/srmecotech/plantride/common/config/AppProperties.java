package com.srmecotech.plantride.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "plantride")
public record AppProperties(
        @NotBlank String timezone,
        @NotBlank String publicBaseUrl,
        @Valid @NotNull Security security,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Dispatch dispatch,
        @Valid @NotNull Matching matching,
        @Valid @NotNull Demo demo) {

    public record Security(@NotBlank String jwtSecret, @Positive long jwtTtlHours) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Dispatch(boolean enabled, @Positive long intervalMs) {
    }

    /**
     * Tuning for the matching engine score. Lower score wins.
     * score = weightEta * eta + weightDetour * detour + weightEmptyKm * emptyKm
     *         - weightPooling * seatsAlreadyTaken + vendorPenalty (vendor cabs only)
     */
    public record Matching(
            @Positive double averageSpeedKmh,
            @Positive double roadFactor,
            int dwellMinutes,
            double weightEta,
            double weightDetour,
            double weightEmptyKm,
            double weightPooling,
            double vendorPenalty) {
    }

    public record Demo(boolean resetEnabled) {
    }
}
