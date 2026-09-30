package com.srmecotech.plantride.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A configured route: its stops in order, and the rules the matching engine
 * applies on it. All of this is master data; changing it needs no release.
 */
@Entity
@Table(name = "route")
@Getter
@Setter
@NoArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "route_type", nullable = false, length = 10)
    private RouteType routeType;

    @Column(name = "service_note", length = 100)
    private String serviceNote;

    /** Shuttle headway in minutes; null when no timetabled shuttle runs this route. */
    @Column(name = "frequency_min")
    private Integer frequencyMin;

    @Column(name = "first_departure", nullable = false)
    private LocalTime firstDeparture;

    /** The occupancy cap: riders per vehicle on pick-up runs (deck: 3). */
    @Column(name = "max_passengers", nullable = false)
    private int maxPassengers;

    /** How long a cab waits at a pickup before the driver may mark a no-show. */
    @Column(name = "max_wait_min", nullable = false)
    private int maxWaitMin;

    /** Extra minutes a new pickup may add for riders already on board. */
    @Column(name = "max_detour_min", nullable = false)
    private int maxDetourMin;

    @Column(name = "speed_limit_kmh", nullable = false)
    private int speedLimitKmh;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "route", fetch = FetchType.LAZY)
    @OrderBy("seq ASC")
    private List<RouteStop> stops = new ArrayList<>();

    public String label() {
        return "Route " + code.replace("R", "") + " · " + name;
    }
}
