package com.srmecotech.plantride.trip;

import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** One driver with one vehicle for one shift, from sign-on to sign-off. */
@Entity
@Table(name = "duty")
@Getter
@Setter
@NoArgsConstructor
public class Duty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private DutyStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "start_odometer", nullable = false)
    private int startOdometer;

    @Column(name = "end_odometer")
    private Integer endOdometer;

    @Column(name = "fuel_litres", precision = 6, scale = 2)
    private BigDecimal fuelLitres;

    /** Comma-separated start-of-duty check items that were ticked. */
    @Column(nullable = false, length = 200)
    private String checklist;

    public long minutesOnDuty(LocalDateTime now) {
        LocalDateTime end = endedAt != null ? endedAt : now;
        return Math.max(0, Duration.between(startedAt, end).toMinutes());
    }
}
