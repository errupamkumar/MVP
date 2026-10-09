package com.srmecotech.plantride.masterdata;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * Moves the live position to a GPS fix when it is newer than the last one
     * (offline buffers flush out of order). A bulk update does not bump the
     * version, so a ping never makes a concurrent "Arrived" tap fail as stale.
     *
     * @return 1 when the fix was the newest and moved the cab, else 0
     */
    @Modifying
    @Query("""
            update Vehicle v
               set v.lastLatitude = :lat, v.lastLongitude = :lng, v.lastSpeedKmh = :speed,
                   v.lastAccuracyM = :accuracy, v.lastHeading = :heading, v.lastPingAt = :at
             where v.id = :id and (v.lastPingAt is null or v.lastPingAt < :at)
            """)
    int moveTo(@Param("id") Long id, @Param("lat") double lat, @Param("lng") double lng, @Param("speed") double speed,
               @Param("accuracy") Integer accuracy, @Param("heading") Integer heading, @Param("at") LocalDateTime at);
}
