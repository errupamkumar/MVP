package com.srmecotech.ecoride.geo;

import com.srmecotech.ecoride.common.config.AppProperties;
import com.srmecotech.ecoride.pricing.VehicleType;
import com.srmecotech.ecoride.pricing.VehicleTypeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** What the apps need before anyone signs in: the city and the ride products (driver sign-up lists them). */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public")
public class PublicController {

    private final AppProperties properties;
    private final ServiceArea serviceArea;
    private final VehicleTypeRepository vehicleTypes;

    public PublicController(AppProperties properties, ServiceArea serviceArea, VehicleTypeRepository vehicleTypes) {
        this.properties = properties;
        this.serviceArea = serviceArea;
        this.vehicleTypes = vehicleTypes;
    }

    @GetMapping("/config")
    @Operation(summary = "City, service area and ride products")
    public Config config() {
        List<Product> products = vehicleTypes.findByActiveTrueOrderBySortOrder().stream()
                .map(PublicController::product)
                .toList();
        GeoPoint c = serviceArea.center();
        return new Config(properties.city(), c.lat(), c.lng(), serviceArea.radiusKm(), products);
    }

    private static Product product(VehicleType vt) {
        return new Product(vt.getCode(), vt.getName(), vt.getDescription(), vt.getCapacity(), vt.getBaseFare(),
                vt.getBaseKm(), vt.getPerKm(), vt.getPerMin());
    }

    public record Config(String city, double centerLat, double centerLng, int serviceRadiusKm, List<Product> products) {
    }

    public record Product(String code, String name, String description, int capacity, BigDecimal baseFare,
                          BigDecimal baseKm, BigDecimal perKm, BigDecimal perMin) {
    }
}
