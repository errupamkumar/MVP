package com.srmecotech.ecoride.geo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A pickup or drop as the app sends it: the coordinates plus the label the rider saw.
 *
 * @param address shown on both apps and the receipt; when blank the server looks it up
 */
public record LocationInput(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Size(max = 255) String address) {

    public GeoPoint point() {
        return new GeoPoint(lat, lng);
    }
}
