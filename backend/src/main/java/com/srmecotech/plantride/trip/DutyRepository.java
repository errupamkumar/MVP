package com.srmecotech.plantride.trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DutyRepository extends JpaRepository<Duty, Long> {

    @Query("""
            select d from Duty d
            join fetch d.vehicle v
            left join fetch v.vendor
            left join fetch v.currentStop
            join fetch d.driver dr
            join fetch dr.user
            left join fetch dr.vendor
            where d.status = com.srmecotech.plantride.trip.DutyStatus.ACTIVE
            """)
    List<Duty> findActiveWithVehicleAndDriver();

    @Query("""
            select d from Duty d
            join fetch d.vehicle v
            left join fetch v.vendor
            left join fetch v.currentStop
            where d.driver.id = :driverId and d.status = com.srmecotech.plantride.trip.DutyStatus.ACTIVE
            """)
    Optional<Duty> findActiveByDriverId(@Param("driverId") Long driverId);

    @Query("select d from Duty d where d.vehicle.id = :vehicleId and d.status = com.srmecotech.plantride.trip.DutyStatus.ACTIVE")
    Optional<Duty> findActiveByVehicleId(@Param("vehicleId") Long vehicleId);
}
