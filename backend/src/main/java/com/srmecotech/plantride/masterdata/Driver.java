package com.srmecotech.plantride.masterdata;

import com.srmecotech.plantride.identity.AppUser;
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

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "driver")
@Getter
@Setter
@NoArgsConstructor
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "driver_code", nullable = false, unique = true, length = 20)
    private String driverCode;

    /** Null for LHS own-fleet drivers. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "licence_no", nullable = false, length = 30)
    private String licenceNo;

    @Column(name = "licence_expiry", nullable = false)
    private LocalDate licenceExpiry;

    @Column(name = "gate_pass_no", nullable = false, length = 20)
    private String gatePassNo;

    @Column(name = "gate_pass_expiry", nullable = false)
    private LocalDate gatePassExpiry;

    @Column(name = "gates_allowed", nullable = false, length = 100)
    private String gatesAllowed;

    @Column(name = "max_duty_minutes", nullable = false)
    private int maxDutyMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_vehicle_id")
    private Vehicle defaultVehicle;

    /** Own-fleet drivers drive own-fleet vehicles; vendor drivers drive their vendor's vehicles. */
    public boolean mayDrive(Vehicle vehicle) {
        Long mine = vendor == null ? null : vendor.getId();
        Long theirs = vehicle.getVendor() == null ? null : vehicle.getVendor().getId();
        return Objects.equals(mine, theirs);
    }
}
