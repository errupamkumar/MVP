package com.srmecotech.ecoride.ride;

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

import java.time.Instant;

/** A ride shown to one driver with an accept countdown. */
@Entity
@Table(name = "ride_offer")
@Getter
@Setter
public class RideOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long rideId;
    private Long driverId;

    @Enumerated(EnumType.STRING)
    private OfferStatus status;

    /** Straight-line distance from the driver to the pickup when offered. */
    @Column(name = "distance_m")
    private int distanceM;
    @Column(name = "eta_s")
    private int etaS;
    private Instant offeredAt;
    private Instant expiresAt;
    private Instant respondedAt;

    public enum OfferStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        /** Not answered in time. */
        EXPIRED,
        /** Withdrawn: the rider cancelled, or the driver went offline or took another ride. */
        CANCELLED
    }
}
