package com.srmecotech.ecoride.geo;

import com.srmecotech.ecoride.common.config.AppProperties;
import com.srmecotech.ecoride.common.error.InvalidRequestException;
import com.srmecotech.ecoride.common.settings.Setting;
import com.srmecotech.ecoride.common.settings.SettingsService;
import org.springframework.stereotype.Component;

@Component
public class ServiceArea {

    private final AppProperties properties;
    private final SettingsService settings;

    public ServiceArea(AppProperties properties, SettingsService settings) {
        this.properties = properties;
        this.settings = settings;
    }

    public GeoPoint center() {
        return new GeoPoint(properties.serviceCenterLat(), properties.serviceCenterLng());
    }

    public int radiusKm() {
        return settings.getInt(Setting.SERVICE_AREA_RADIUS_KM);
    }

    public boolean contains(GeoPoint point) {
        return GeoMath.distanceM(center(), point) <= radiusKm() * 1000d;
    }

    public void require(GeoPoint point, String what) {
        if (!contains(point)) {
            throw new InvalidRequestException("OUTSIDE_SERVICE_AREA",
                    "%s is outside the EcoRide service area (%s, within %d km of the city centre)."
                            .formatted(what, properties.city(), radiusKm()));
        }
    }
}
