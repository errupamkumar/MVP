package com.srmecotech.plantride.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stop")
@Getter
@Setter
@NoArgsConstructor
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String zone;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** Where exactly to wait, as the rider sees it on the ground (e.g. "Security cabin, visitor lane"). */
    @Column(length = 160)
    private String landmark;

    /** Radius (m) inside which a GPS ping counts as "arrived at this stop". */
    @Column(name = "geofence_m", nullable = false)
    private Integer geofenceM;

    @Column(nullable = false)
    private boolean active = true;
}
