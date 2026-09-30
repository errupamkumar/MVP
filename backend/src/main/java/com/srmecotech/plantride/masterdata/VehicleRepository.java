package com.srmecotech.plantride.masterdata;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Query("""
            select v from Vehicle v
            left join fetch v.vendor
            left join fetch v.currentStop
            left join fetch v.homeRoute
            order by v.code
            """)
    List<Vehicle> findAllForBoard();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> lockById(@Param("id") Long id);
}
