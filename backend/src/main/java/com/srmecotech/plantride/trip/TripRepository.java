package com.srmecotech.plantride.trip;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    @Query("""
            select t from Trip t
            join fetch t.vehicle v
            left join fetch v.vendor
            left join fetch v.currentStop
            join fetch t.driver d
            join fetch d.user
            join fetch t.route
            where t.status in (com.srmecotech.plantride.trip.TripStatus.PLANNED,
                               com.srmecotech.plantride.trip.TripStatus.IN_PROGRESS)
            """)
    List<Trip> findOpenTrips();

    @Query("""
            select count(t) > 0 from Trip t
            where t.vehicle.id = :vehicleId
              and t.status in (com.srmecotech.plantride.trip.TripStatus.PLANNED,
                               com.srmecotech.plantride.trip.TripStatus.IN_PROGRESS)
            """)
    boolean hasOpenTrip(@Param("vehicleId") Long vehicleId);

    @Query("""
            select t from Trip t
            join fetch t.route
            join fetch t.vehicle
            where t.driver.id = :driverId
              and t.status in (com.srmecotech.plantride.trip.TripStatus.PLANNED,
                               com.srmecotech.plantride.trip.TripStatus.IN_PROGRESS)
            """)
    Optional<Trip> findOpenByDriverId(@Param("driverId") Long driverId);

    @Query("""
            select t from Trip t
            join fetch t.route
            where t.vehicle.id = :vehicleId
              and t.status in (com.srmecotech.plantride.trip.TripStatus.PLANNED,
                               com.srmecotech.plantride.trip.TripStatus.IN_PROGRESS)
            """)
    Optional<Trip> findOpenByVehicleId(@Param("vehicleId") Long vehicleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> lockById(@Param("id") Long id);

    List<Trip> findByDutyIdAndStatus(Long dutyId, TripStatus status);
}
