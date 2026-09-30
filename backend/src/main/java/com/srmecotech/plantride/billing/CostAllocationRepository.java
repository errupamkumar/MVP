package com.srmecotech.plantride.billing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CostAllocationRepository extends JpaRepository<CostAllocation, Long> {

    boolean existsByBookingId(Long bookingId);
}
