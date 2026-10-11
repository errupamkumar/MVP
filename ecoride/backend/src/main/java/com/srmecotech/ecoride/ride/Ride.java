package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.geo.GeoPoint;
import com.srmecotech.ecoride.pricing.FareBreakdown;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One ride from request to receipt.
 * <p>
 * Every state change locks this row first ({@link RideRepository#lockById}), then the
 * driver row: one lock order everywhere, so accept, complete and GPS pings never deadlock.
 * The rider's shared position is written by a bulk update and @DynamicUpdate keeps an
 * entity save from overwriting it.
 */
@Entity
@Table(name = "ride")
@Getter
@Setter
@DynamicUpdate
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private Long riderId;
    private Long driverId;
    private String vehicleType;

    @Enumerated(EnumType.STRING)
    private RideStatus status;

    private double pickupLat;
    private double pickupLng;
    private String pickupAddress;
    private double dropLat;
    private double dropLng;
    private String dropAddress;

    /** The planned road route from pickup to drop, as quoted. */
    @Column(name = "est_distance_m")
    private int estDistanceM;
    @Column(name = "est_duration_s")
    private int estDurationS;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String routePolyline;

    private String routeProvider;

    /** The road route from where the driver accepted to the pickup. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String pickupPolyline;

    @Column(name = "pickup_distance_m")

    private Integer pickupDistanceM;
    @Column(name = "pickup_duration_s")
    private Integer pickupDurationS;

    private BigDecimal surgeMultiplier;
    private String promoCode;

    /** The upfront fare the rider accepted. */
    @JdbcTypeCode(SqlTypes.JSON)
    private FareBreakdown quote;

    private BigDecimal estFare;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    private String otp;

    private Instant requestedAt;
    /** Reset when a driver cancels and the search starts again. */
    private Instant searchStartedAt;
    private Instant acceptedAt;
    private Instant arrivedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    /** RIDER, DRIVER, ADMIN or SYSTEM. */
    private String cancelledBy;
    private String cancelReason;
    private BigDecimal cancellationFee;

    /** GPS distance with the rider on board: the "actual" distance on the receipt. */
    @Column(name = "actual_distance_m")
    private int actualDistanceM;
    /** GPS distance driven to reach the pickup. */
    @Column(name = "pickup_travel_m")
    private int pickupTravelM;
    private Double lastTrackLat;
    private Double lastTrackLng;
    private Instant lastTrackAt;

    /** The rider's own position, shared with the driver while waiting for pickup. */
    private Double riderLat;
    private Double riderLng;
    private Instant riderLocatedAt;

    /** The final fare (null until completed). */
    @JdbcTypeCode(SqlTypes.JSON)
    private FareBreakdown fare;

    private BigDecimal fareTotal;
    /** UPFRONT (quoted fare stands) or RECALCULATED (GPS distance differed beyond the tolerance). */
    private String farePolicy;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal commission;
    private BigDecimal driverEarning;
    private BigDecimal tip;

    /** The rider's rating of the driver. */
    private Integer driverRating;
    private String driverFeedback;
    /** The driver's rating of the rider. */
    private Integer riderRating;

    private boolean simulated;

    public GeoPoint pickup() {
        return new GeoPoint(pickupLat, pickupLng);
    }

    public GeoPoint drop() {
        return new GeoPoint(dropLat, dropLng);
    }
}
