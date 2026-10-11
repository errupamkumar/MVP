package com.srmecotech.ecoride.ride;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** An emergency raised from either app during a ride; the admin console shows it until resolved. */
@Entity
@Table(name = "sos_alert")
@Getter
@Setter
public class SosAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long rideId;
    /** RIDER or DRIVER. */
    private String raisedBy;
    private Double lat;
    private Double lng;
    /** OPEN or RESOLVED. */
    private String status;
    private String note;
    private Instant createdAt;
    private Instant resolvedAt;
    private String resolvedBy;
}
