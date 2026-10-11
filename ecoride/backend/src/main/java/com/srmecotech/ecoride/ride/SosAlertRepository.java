package com.srmecotech.ecoride.ride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SosAlertRepository extends JpaRepository<SosAlert, Long> {

    List<SosAlert> findTop50ByOrderByIdDesc();

    List<SosAlert> findByRideIdOrderByIdAsc(Long rideId);

    long countByStatus(String status);
}
