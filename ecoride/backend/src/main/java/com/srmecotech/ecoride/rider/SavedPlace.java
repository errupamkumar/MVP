package com.srmecotech.ecoride.rider;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Home, Work and other places a rider books often. */
@Entity
@Table(name = "saved_place")
@Getter
@Setter
public class SavedPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long riderId;
    /** HOME, WORK or OTHER. */
    private String label;
    private String name;
    private String address;
    private double lat;
    private double lng;
    private Instant createdAt;
}
