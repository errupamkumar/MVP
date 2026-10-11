package com.srmecotech.ecoride.ride;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Money taken from (or returned to) a rider: the fare, a tip, a cancellation fee, a refund. */
@Entity
@Table(name = "payment")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long rideId;
    private Long riderId;
    /** RIDE, TIP, CANCEL_FEE or REFUND. */
    private String kind;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String reference;
    private Instant createdAt;
    private Instant updatedAt;
}
