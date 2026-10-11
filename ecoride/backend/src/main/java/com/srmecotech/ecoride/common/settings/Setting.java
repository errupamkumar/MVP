package com.srmecotech.ecoride.common.settings;

/**
 * Business rules the operations team changes from the admin console without a release.
 * The table stores values only; type, bounds and wording live here.
 */
public enum Setting {

    // ---------------------------------------------------------------- dispatch
    DISPATCH_RADIUS_M("Dispatch", "Search radius", "Drivers farther than this from the pickup are not offered the ride.",
            Type.INT, "5000", 500, 20000, "m"),
    OFFER_TIMEOUT_S("Dispatch", "Offer timeout", "Seconds a driver has to accept before the ride moves to the next driver.",
            Type.INT, "15", 5, 60, "s"),
    SEARCH_TIMEOUT_S("Dispatch", "Search timeout", "After this long without an accepting driver the rider is told no cars are available.",
            Type.INT, "120", 30, 600, "s"),
    DRIVER_LOCATION_FRESH_S("Dispatch", "Location freshness", "A driver whose last GPS fix is older than this gets no offers.",
            Type.INT, "90", 15, 600, "s"),
    PREFER_REAL_DRIVERS("Dispatch", "Prefer real drivers (demo)", "Simulated drivers are offered a real rider's ride only after every real driver in range has passed.",
            Type.BOOL, "true", 0, 1, ""),
    ROAD_FACTOR("Dispatch", "Road factor", "Straight-line distance x this factor estimates road distance when ranking drivers.",
            Type.DECIMAL, "1.35", 1, 3, "x"),
    CITY_SPEED_KMH("Dispatch", "City speed", "Average speed used for pickup ETAs when no road route is available.",
            Type.INT, "22", 5, 80, "km/h"),
    TRAFFIC_FACTOR("Dispatch", "Traffic factor", "Road-route durations (free-flow) are multiplied by this to reflect city traffic.",
            Type.DECIMAL, "1.40", 1, 3, "x"),

    // ---------------------------------------------------------------- trip
    ARRIVAL_RADIUS_M("Trip", "Arrival radius", "A driver can mark 'Arrived' only within this distance of the pickup.",
            Type.INT, "250", 30, 2000, "m"),
    NO_SHOW_WAIT_MIN("Trip", "No-show wait", "After waiting this long at the pickup a driver may cancel as a rider no-show (the rider pays the cancellation fee).",
            Type.INT, "5", 1, 30, "min"),
    RIDER_CANCEL_GRACE_S("Trip", "Free cancellation window", "A rider may cancel free of charge for this long after a driver accepts.",
            Type.INT, "120", 0, 900, "s"),
    FARE_RECALC_TOLERANCE_PCT("Trip", "Upfront fare tolerance", "The upfront fare stands unless the GPS distance differs from the planned route by more than this.",
            Type.INT, "20", 0, 100, "%"),
    GPS_MAX_SPEED_KMH("Trip", "GPS jump filter", "A GPS fix implying a faster speed than this is treated as a jump and not added to the trip distance.",
            Type.INT, "150", 60, 400, "km/h"),

    // ---------------------------------------------------------------- money
    COMMISSION_PCT("Money", "Platform commission", "Share of the fare (before tax) the platform keeps. MoRTH 2025 guidelines: drivers keep at least 80%.",
            Type.INT, "20", 0, 50, "%"),
    GST_PCT("Money", "GST on rides", "Tax added to the fare.",
            Type.DECIMAL, "5", 0, 28, "%"),
    DRIVER_CANCEL_PENALTY_PCT("Money", "Driver cancellation penalty", "Share of the fare charged to a driver who cancels an accepted ride without cause.",
            Type.INT, "10", 0, 100, "%"),
    DRIVER_CANCEL_PENALTY_CAP("Money", "Driver penalty cap", "Upper limit of the driver cancellation penalty.",
            Type.INT, "100", 0, 1000, "₹"),

    // ---------------------------------------------------------------- surge
    SURGE_ENABLED("Surge", "Surge pricing", "Turn dynamic pricing on or off everywhere.",
            Type.BOOL, "true", 0, 1, ""),
    SURGE_CAP("Surge", "Surge cap", "Highest multiplier ever charged. Maharashtra caps surge at 1.5x the base fare.",
            Type.DECIMAL, "1.5", 1, 3, "x"),
    SURGE_RADIUS_M("Surge", "Demand radius", "Requests and idle drivers within this distance count towards automatic surge.",
            Type.INT, "3000", 500, 10000, "m"),

    // ---------------------------------------------------------------- service
    SERVICE_AREA_RADIUS_KM("Service", "Service area radius", "Pickups and drops must be within this distance of the city centre.",
            Type.INT, "35", 5, 200, "km"),
    QUOTE_TTL_S("Service", "Upfront price validity", "How long a fare shown to the rider is held.",
            Type.INT, "300", 60, 1800, "s"),

    // ---------------------------------------------------------------- demo
    DEMO_FLEET_ENABLED("Demo", "Simulated drivers", "Simulated drivers cruise the city, accept rides and drive them.",
            Type.BOOL, "true", 0, 1, ""),
    DEMO_DEMAND_ENABLED("Demo", "Simulated riders", "Simulated riders request rides that only simulated drivers serve.",
            Type.BOOL, "true", 0, 1, ""),
    DEMO_DEMAND_INTERVAL_S("Demo", "Simulated request interval", "Seconds between simulated ride requests.",
            Type.INT, "40", 10, 600, "s"),
    DEMO_SIM_SPEED_X("Demo", "Simulated trip speed-up", "Simulated drivers drive rides this many times faster than real time.",
            Type.INT, "6", 1, 30, "x");

    public enum Type { INT, DECIMAL, BOOL }

    private final String category;
    private final String label;
    private final String description;
    private final Type type;
    private final String defaultValue;
    private final double min;
    private final double max;
    private final String unit;

    Setting(String category, String label, String description, Type type, String defaultValue,
            double min, double max, String unit) {
        this.category = category;
        this.label = label;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.unit = unit;
    }

    public String category() {
        return category;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Type type() {
        return type;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public String unit() {
        return unit;
    }
}
