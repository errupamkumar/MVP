package com.srmecotech.plantride.booking;

import com.srmecotech.plantride.identity.AppUser;
import com.srmecotech.plantride.identity.Employee;
import com.srmecotech.plantride.masterdata.CostCentre;
import com.srmecotech.plantride.masterdata.Route;
import com.srmecotech.plantride.masterdata.Stop;
import com.srmecotech.plantride.trip.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 12)
    private String bookingCode;

    /** Idempotency-Key sent by the app; a retried "Confirm ride" returns the same booking. */
    @Column(name = "client_request_id", unique = true, length = 64)
    private String clientRequestId;

    /** The employee whose cost centre pays (the host, when booking for a guest). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booked_by_user_id", nullable = false)
    private AppUser bookedBy;

    @Column(name = "rider_name", nullable = false, length = 100)
    private String riderName;

    @Column(name = "rider_phone", nullable = false, length = 20)
    private String riderPhone;

    @Column(name = "rider_email", length = 120)
    private String riderEmail;

    @Column(name = "for_guest", nullable = false)
    private boolean forGuest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_stop_id", nullable = false)
    private Stop fromStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_stop_id", nullable = false)
    private Stop toStop;

    @Column(name = "from_seq", nullable = false)
    private int fromSeq;

    @Column(name = "to_seq", nullable = false)
    private int toSeq;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ride_type", nullable = false, length = 10)
    private RideType rideType;

    @Column(nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    /** Requested pickup time; null means "as soon as possible". */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(length = 4)
    private String otp;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(name = "tracking_token", nullable = false, unique = true, length = 16)
    private String trackingToken;

    /** ETA to pickup promised when the cab was assigned. */
    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "promised_pickup_at")
    private LocalDateTime promisedPickupAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "boarded_at")
    private LocalDateTime boardedAt;

    @Column(name = "dropped_at")
    private LocalDateTime droppedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cost_centre_id", nullable = false)
    private CostCentre costCentre;

    @Column(name = "distance_km", precision = 7, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "cost_amount", precision = 10, scale = 2)
    private BigDecimal costAmount;

    /** Why the engine picked this cab (or why none was free): the desk's audit of the decision. */
    @Column(name = "match_note", length = 300)
    private String matchNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private AppUser approvedBy;

    @Column
    private Integer rating;

    @Column(name = "rating_tags", length = 200)
    private String ratingTags;

    @Column(name = "rating_note", length = 500)
    private String ratingNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private long version;

    public boolean isActive() {
        return BookingStatus.ACTIVE.contains(status);
    }

    /** Takes the booking off its trip and puts it back in the dispatch queue. */
    public void returnToQueue(LocalDateTime now) {
        this.trip = null;
        this.status = BookingStatus.REQUESTED;
        this.assignedAt = null;
        this.boardedAt = null;
        this.etaMinutes = null;
        this.promisedPickupAt = null;
        this.otpAttempts = 0;
        this.updatedAt = now;
    }

    public void close(BookingStatus closedAs, String reason, LocalDateTime now) {
        this.status = closedAs;
        this.cancelReason = reason;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public void truncateMatchNote() {
        if (matchNote != null && matchNote.length() > 300) {
            matchNote = matchNote.substring(0, 297) + "...";
        }
    }
}
