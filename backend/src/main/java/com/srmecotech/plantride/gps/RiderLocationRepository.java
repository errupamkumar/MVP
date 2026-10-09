package com.srmecotech.plantride.gps;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RiderLocationRepository extends JpaRepository<RiderLocation, Long> {

    /** Insert or replace in one statement, so two quick pings never race on the primary key. */
    @Modifying
    @Query(value = """
            INSERT INTO rider_location (booking_id, latitude, longitude, accuracy_m, recorded_at, received_at)
            VALUES (:bookingId, :lat, :lng, :accuracy, :recordedAt, :receivedAt) AS fix
            ON DUPLICATE KEY UPDATE latitude = fix.latitude, longitude = fix.longitude, accuracy_m = fix.accuracy_m,
                                    recorded_at = fix.recorded_at, received_at = fix.received_at
            """, nativeQuery = true)
    void upsert(@Param("bookingId") Long bookingId, @Param("lat") double lat, @Param("lng") double lng,
                @Param("accuracy") Integer accuracy, @Param("recordedAt") LocalDateTime recordedAt,
                @Param("receivedAt") LocalDateTime receivedAt);

    @Modifying
    @Query("delete from RiderLocation r where r.bookingId = :bookingId")
    int forget(@Param("bookingId") Long bookingId);

    /** Retention: drop positions of riders no longer waiting for their cab, and any fix older than the cutoff. */
    @Modifying
    @Query(value = """
            DELETE r FROM rider_location r
            JOIN booking b ON b.id = r.booking_id
            WHERE b.status <> 'ASSIGNED' OR r.received_at < :cutoff
            """, nativeQuery = true)
    int purge(@Param("cutoff") LocalDateTime cutoff);
}
