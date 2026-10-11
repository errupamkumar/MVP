package com.srmecotech.ecoride.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, String> {

    List<VehicleType> findAllByOrderBySortOrder();

    List<VehicleType> findByActiveTrueOrderBySortOrder();
}
