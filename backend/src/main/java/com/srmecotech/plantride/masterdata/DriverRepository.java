package com.srmecotech.plantride.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    @Query("""
            select d from Driver d
            join fetch d.user
            left join fetch d.vendor
            left join fetch d.defaultVehicle
            where d.user.id = :userId
            """)
    Optional<Driver> findByUserId(@Param("userId") Long userId);

    @Query("select d from Driver d join fetch d.user left join fetch d.vendor order by d.driverCode")
    List<Driver> findAllWithUser();
}
