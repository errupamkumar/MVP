package com.srmecotech.plantride.billing;

import com.srmecotech.plantride.masterdata.Vendor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What a vendor billed for a month, checked against GPS kilometres. */
@Entity
@Table(name = "vendor_claim")
@Getter
@Setter
@NoArgsConstructor
public class VendorClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "claimed_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal claimedKm;

    @Column(name = "claimed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}
