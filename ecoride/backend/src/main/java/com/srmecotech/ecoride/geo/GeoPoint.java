package com.srmecotech.ecoride.geo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record GeoPoint(
        @DecimalMin("-90") @DecimalMax("90") double lat,
        @DecimalMin("-180") @DecimalMax("180") double lng) {

    public double distanceTo(GeoPoint other) {
        return GeoMath.distanceM(lat, lng, other.lat, other.lng);
    }

    @Override
    public String toString() {
        return "%.5f,%.5f".formatted(lat, lng);
    }
}
