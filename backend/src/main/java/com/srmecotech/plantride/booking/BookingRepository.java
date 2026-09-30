package com.srmecotech.plantride.booking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
            join fetch b.fromStop
            join fetch b.toStop
            join fetch b.employee e
            join fetch e.user
            where b.trip.id = :tripId and b.status in :statuses
            order by b.fromSeq, b.createdAt
            """)
    List<Booking> findOnTrip(@Param("tripId") Long tripId, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.trip.id in :tripIds and b.status in :statuses")
    List<Booking> findOnTrips(@Param("tripIds") Collection<Long> tripIds,
                              @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            select b from Booking b
            join fetch b.fromStop
            join fetch b.toStop
            join fetch b.route
            join fetch b.costCentre
            join fetch b.employee e
            join fetch e.user
            left join fetch b.trip t
            left join fetch t.vehicle
            left join fetch t.driver d
            left join fetch d.user
            where b.id = :id
            """)
    Optional<Booking> findDetailed(@Param("id") Long id);

    Optional<Booking> findByClientRequestId(String clientRequestId);

    @Query("""
            select b from Booking b
            join fetch b.fromStop
            join fetch b.toStop
            join fetch b.route
            left join fetch b.trip t
            left join fetch t.vehicle
            where b.trackingToken = :token
            """)
    Optional<Booking> findByTrackingToken(@Param("token") String token);

    boolean existsByBookingCode(String bookingCode);

    boolean existsByTrackingToken(String trackingToken);

    @Query("select b.id from Booking b where b.status = :status order by b.createdAt asc, b.id asc")
    List<Long> findIdsByStatus(@Param("status") BookingStatus status);

    @Query("""
            select b from Booking b
            where b.status = com.srmecotech.plantride.booking.BookingStatus.SCHEDULED
              and b.scheduledAt <= :threshold
            """)
    List<Booking> findScheduledDueBy(@Param("threshold") LocalDateTime threshold);

    @Query("""
            select count(b) from Booking b
            where b.employee.id = :employeeId
              and b.status = com.srmecotech.plantride.booking.BookingStatus.NO_SHOW
              and b.cancelledAt >= :since
            """)
    long countNoShowsSince(@Param("employeeId") Long employeeId, @Param("since") LocalDateTime since);

    /**
     * An open "ride now" (anything not scheduled beyond the horizon) that
     * would clash with a new immediate booking.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.employee.id = :employeeId
              and b.forGuest = false
              and b.status in (com.srmecotech.plantride.booking.BookingStatus.PENDING_APPROVAL,
                               com.srmecotech.plantride.booking.BookingStatus.REQUESTED,
                               com.srmecotech.plantride.booking.BookingStatus.ASSIGNED,
                               com.srmecotech.plantride.booking.BookingStatus.ONBOARD)
              and (b.scheduledAt is null or b.scheduledAt <= :horizon)
            """)
    boolean hasOpenImmediateRide(@Param("employeeId") Long employeeId, @Param("horizon") LocalDateTime horizon);

    long countByEmployeeIdAndStatus(Long employeeId, BookingStatus status);

    @Query("""
            select coalesce(sum(b.seats), 0) from Booking b
            where b.trip.duty.id = :dutyId
              and b.status = com.srmecotech.plantride.booking.BookingStatus.COMPLETED
            """)
    long sumCompletedSeatsForDuty(@Param("dutyId") Long dutyId);

    @Query("""
            select b from Booking b
            join fetch b.fromStop
            join fetch b.toStop
            left join fetch b.trip t
            left join fetch t.vehicle
            where b.employee.id = :employeeId and b.status in :statuses
            order by coalesce(b.scheduledAt, b.createdAt) desc, b.id desc
            """)
    List<Booking> findForEmployee(@Param("employeeId") Long employeeId,
                                  @Param("statuses") Collection<BookingStatus> statuses,
                                  Pageable pageable);

    @Query("""
            select b from Booking b
            where b.employee.id = :employeeId
              and b.status in (com.srmecotech.plantride.booking.BookingStatus.PENDING_APPROVAL,
                               com.srmecotech.plantride.booking.BookingStatus.REQUESTED,
                               com.srmecotech.plantride.booking.BookingStatus.ASSIGNED,
                               com.srmecotech.plantride.booking.BookingStatus.ONBOARD)
            order by b.createdAt desc
            """)
    List<Booking> findLiveForEmployee(@Param("employeeId") Long employeeId, Pageable pageable);

    @Query("""
            select b from Booking b
            join fetch b.toStop
            join fetch b.fromStop
            where b.employee.id = :employeeId
              and b.status = com.srmecotech.plantride.booking.BookingStatus.COMPLETED
              and b.droppedAt >= :since
            order by b.droppedAt desc
            """)
    List<Booking> findCompletedSince(@Param("employeeId") Long employeeId, @Param("since") LocalDateTime since);

    @Query("""
            select b from Booking b
            join fetch b.fromStop
            join fetch b.toStop
            join fetch b.route
            join fetch b.costCentre
            join fetch b.employee e
            join fetch e.user
            where b.status in :statuses
            order by b.createdAt asc
            """)
    List<Booking> findQueue(@Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            select count(b) > 0 from Booking b
            where b.route.id = :routeId
              and b.status in (com.srmecotech.plantride.booking.BookingStatus.SCHEDULED,
                               com.srmecotech.plantride.booking.BookingStatus.PENDING_APPROVAL,
                               com.srmecotech.plantride.booking.BookingStatus.REQUESTED,
                               com.srmecotech.plantride.booking.BookingStatus.ASSIGNED,
                               com.srmecotech.plantride.booking.BookingStatus.ONBOARD)
            """)
    boolean hasActiveOnRoute(@Param("routeId") Long routeId);
}
