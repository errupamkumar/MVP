package com.srmecotech.ecoride.driver;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByPhone(String phone);

    Optional<Driver> findByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByVehiclePlate(String plate);

    List<Driver> findBySimulatedTrueAndStatus(DriverStatus status);

    @Query("""
            select d from Driver d
            where (d.simulated = false or :includeSimulated = true)
            and (:status is null or d.status = :status)
            and (:availability is null or d.availability = :availability)
            and (:q is null or lower(d.name) like lower(concat('%', :q, '%')) or d.phone like concat('%', :q, '%')
                 or lower(d.vehiclePlate) like lower(concat('%', :q, '%')))
            """)
    Page<Driver> search(@Param("q") String q, @Param("status") DriverStatus status,
                        @Param("availability") Availability availability,
                        @Param("includeSimulated") boolean includeSimulated, Pageable pageable);

    @Query("select d from Driver d where d.availability <> com.srmecotech.ecoride.driver.Availability.OFFLINE and d.lat is not null")
    List<Driver> findAllOnMap();

    /** A GPS fix. Bulk update: no entity is loaded, so a concurrent status change is never overwritten. */
    @Modifying
    @Query("""
            update Driver d set d.lat = :lat, d.lng = :lng, d.heading = :heading, d.speedKmh = :speed, d.locatedAt = :at
            where d.id = :id
            """)
    int updateLocation(@Param("id") Long id, @Param("lat") double lat, @Param("lng") double lng,
                       @Param("heading") Integer heading, @Param("speed") Double speed, @Param("at") Instant at);

    /** Compare-and-set on availability: 0 rows means someone else changed it first. */
    @Modifying(flushAutomatically = true)
    @Query("update Driver d set d.availability = :to where d.id = :id and d.availability = :from")
    int transition(@Param("id") Long id, @Param("from") Availability from, @Param("to") Availability to);

    @Modifying(flushAutomatically = true)
    @Query("update Driver d set d.availability = :to, d.onlineSince = :since where d.id = :id")
    int setAvailability(@Param("id") Long id, @Param("to") Availability to, @Param("since") Instant since);

    @Modifying(flushAutomatically = true)
    @Query("update Driver d set d.offersReceived = d.offersReceived + 1 where d.id = :id")
    int countOffer(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Driver d set d.offersAccepted = d.offersAccepted + 1 where d.id = :id")
    int countAccept(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE driver SET rating_avg = ROUND((rating_avg * rating_count + :stars) / (rating_count + 1), 2),
                              rating_count = rating_count + 1
            WHERE id = :id
            """, nativeQuery = true)
    int addRating(@Param("id") Long id, @Param("stars") int stars);
}
