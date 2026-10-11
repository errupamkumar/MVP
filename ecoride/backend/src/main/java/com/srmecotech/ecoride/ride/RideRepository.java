package com.srmecotech.ecoride.ride;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    /** SELECT ... FOR UPDATE: every state change goes through this. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> lockById(@Param("id") Long id);

    Optional<Ride> findFirstByRiderIdAndStatusInOrderByIdDesc(Long riderId, Collection<RideStatus> statuses);

    Optional<Ride> findFirstByDriverIdAndStatusInOrderByIdDesc(Long driverId, Collection<RideStatus> statuses);

    @Query("select r.id from Ride r where r.driverId = :driverId and r.status in :statuses")
    List<Long> findIdsByDriverAndStatus(@Param("driverId") Long driverId, @Param("statuses") Collection<RideStatus> statuses);

    @Query("select r.id from Ride r where r.status = com.srmecotech.ecoride.ride.RideStatus.SEARCHING order by r.id")
    List<Long> findSearchingIds();

    List<Ride> findByStatusIn(Collection<RideStatus> statuses);

    Page<Ride> findByRiderIdOrderByIdDesc(Long riderId, Pageable pageable);

    Page<Ride> findByDriverIdAndStatusInOrderByIdDesc(Long driverId, Collection<RideStatus> statuses, Pageable pageable);

    @Query("""
            select r from Ride r
            where (:status is null or r.status = :status)
            and (:vehicleType is null or r.vehicleType = :vehicleType)
            and (:includeSimulated = true or r.simulated = false)
            and (:riderId is null or r.riderId = :riderId)
            and (:driverId is null or r.driverId = :driverId)
            and (:q is null or r.code like concat('%', upper(:q), '%')
                 or lower(r.pickupAddress) like lower(concat('%', :q, '%'))
                 or lower(r.dropAddress) like lower(concat('%', :q, '%')))
            order by r.id desc
            """)
    Page<Ride> search(@Param("q") String q, @Param("status") RideStatus status, @Param("vehicleType") String vehicleType,
                      @Param("riderId") Long riderId, @Param("driverId") Long driverId,
                      @Param("includeSimulated") boolean includeSimulated, Pageable pageable);

    /** The rider's shared position. Bulk update: never bumps or overwrites anything a state change wrote. */
    @Modifying
    @Query("update Ride r set r.riderLat = :lat, r.riderLng = :lng, r.riderLocatedAt = :at where r.id = :id")
    int updateRiderLocation(@Param("id") Long id, @Param("lat") double lat, @Param("lng") double lng,
                            @Param("at") Instant at);

    @Query("select count(r) from Ride r where r.driverId = :driverId and r.status = com.srmecotech.ecoride.ride.RideStatus.COMPLETED")
    long countCompletedByDriver(@Param("driverId") Long driverId);

    @Query("select count(r) from Ride r where r.riderId = :riderId and r.status = com.srmecotech.ecoride.ride.RideStatus.COMPLETED")
    long countCompletedByRider(@Param("riderId") Long riderId);

    @Query("""
            select count(r) from Ride r where r.driverId = :driverId and r.status = com.srmecotech.ecoride.ride.RideStatus.COMPLETED
            and r.completedAt >= :from and r.completedAt < :to
            """)
    long countCompletedByDriverBetween(@Param("driverId") Long driverId, @Param("from") Instant from, @Param("to") Instant to);
}
