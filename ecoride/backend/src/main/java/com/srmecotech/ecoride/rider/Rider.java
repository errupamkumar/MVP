package com.srmecotech.ecoride.rider;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A rider account (rider app). Wallet balance and rating are changed only by atomic SQL
 * updates in {@link RiderRepository}; @DynamicUpdate keeps an entity save from writing
 * back a stale copy of them.
 */
@Entity
@Table(name = "rider")
@Getter
@Setter
@DynamicUpdate
public class Rider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String phone;
    private String email;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private RiderStatus status;

    private String statusReason;
    private BigDecimal ratingAvg;
    private int ratingCount;
    private BigDecimal walletBalance;
    private boolean simulated;
    private Instant createdAt;
    private Instant lastLoginAt;
}
