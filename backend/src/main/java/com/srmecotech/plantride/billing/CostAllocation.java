package com.srmecotech.plantride.billing;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.masterdata.CostCentre;
import com.srmecotech.plantride.trip.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One ride's share of a trip's cost, posted to a cost centre for a billing period. */
@Entity
@Table(name = "cost_allocation")
@Getter
@Setter
@NoArgsConstructor
public class CostAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cost_centre_id", nullable = false)
    private CostCentre costCentre;

    /** Billing month, yyyy-MM. */
    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "distance_km", nullable = false, precision = 7, scale = 2)
    private BigDecimal distanceKm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "sharing_rule", nullable = false, length = 10)
    private String sharingRule;

    @Column(name = "riders_on_trip", nullable = false)
    private int ridersOnTrip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
