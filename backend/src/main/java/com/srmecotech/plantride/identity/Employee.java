package com.srmecotech.plantride.identity;

import com.srmecotech.plantride.masterdata.CostCentre;
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

/** The HR record behind a rider login (in production, synced from HRMS). */
@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    /** Personnel number, e.g. LHS-40218 ("P. No." on the booking screen). */
    @Column(name = "p_no", nullable = false, unique = true, length = 20)
    private String personnelNo;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(nullable = false, length = 10)
    private String grade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cost_centre_id", nullable = false)
    private CostCentre costCentre;

    /** Grade rule: exclusive rides are released without a separate approval. */
    @Column(name = "exclusive_eligible", nullable = false)
    private boolean exclusiveEligible;
}
