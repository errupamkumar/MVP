package com.srmecotech.ecoride.pricing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "promo_code")
@Getter
@Setter
public class PromoCode {

    @Id
    private String code;

    private String description;
    /** Percentage off (with {@link #maxDiscount} as the cap), or null for a flat discount. */
    private Integer percentOff;
    private BigDecimal flatOff;
    private BigDecimal maxDiscount;
    /** The fare (before discount) must be at least this. */
    private BigDecimal minFare;
    private int usesPerRider;
    private LocalDate validUntil;
    private boolean active;
    private Instant createdAt;

    public FareCalculator.Promo toPromo() {
        return new FareCalculator.Promo(code, percentOff, flatOff, maxDiscount);
    }
}
