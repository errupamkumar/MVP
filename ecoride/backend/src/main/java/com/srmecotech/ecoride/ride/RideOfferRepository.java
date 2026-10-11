package com.srmecotech.ecoride.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RideOfferRepository extends JpaRepository<RideOffer, Long> {

    Optional<RideOffer> findFirstByDriverIdAndStatusOrderByIdAsc(Long driverId, RideOffer.OfferStatus status);

    Optional<RideOffer> findFirstByRideIdAndStatus(Long rideId, RideOffer.OfferStatus status);

    List<RideOffer> findByRideIdOrderByIdAsc(Long rideId);

    long countByRideId(Long rideId);

    @Query("select o.rideId from RideOffer o where o.status = com.srmecotech.ecoride.ride.RideOffer.OfferStatus.PENDING and o.expiresAt < :now")
    List<Long> findRideIdsWithExpiredOffers(@Param("now") Instant now);

    /** Withdraws a driver's other open offers (they went offline or took another ride). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RideOffer o set o.status = com.srmecotech.ecoride.ride.RideOffer.OfferStatus.CANCELLED, o.respondedAt = :now
            where o.driverId = :driverId and o.status = com.srmecotech.ecoride.ride.RideOffer.OfferStatus.PENDING
            and (:exceptId is null or o.id <> :exceptId)
            """)
    int cancelPendingForDriver(@Param("driverId") Long driverId, @Param("exceptId") Long exceptId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RideOffer o set o.status = com.srmecotech.ecoride.ride.RideOffer.OfferStatus.CANCELLED, o.respondedAt = :now
            where o.rideId = :rideId and o.status = com.srmecotech.ecoride.ride.RideOffer.OfferStatus.PENDING
            """)
    int cancelPendingForRide(@Param("rideId") Long rideId, @Param("now") Instant now);
}
