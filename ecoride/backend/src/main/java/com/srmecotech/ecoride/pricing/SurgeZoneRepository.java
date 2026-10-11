package com.srmecotech.ecoride.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurgeZoneRepository extends JpaRepository<SurgeZone, Long> {

    List<SurgeZone> findAllByOrderByName();

    List<SurgeZone> findByActiveTrue();
}
