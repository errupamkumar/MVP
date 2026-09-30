package com.srmecotech.plantride.notification;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.identity.AppUser;
import com.srmecotech.plantride.notification.dto.NotificationDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_WAITING = "BOOKING_WAITING";
    public static final String CAB_ARRIVING = "CAB_ARRIVING";
    public static final String TRIP_STARTED = "TRIP_STARTED";
    public static final String TRIP_COMPLETED = "TRIP_COMPLETED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
    public static final String NO_SHOW = "NO_SHOW";
    public static final String REMATCHED = "REMATCHED";
    public static final String APPROVAL = "APPROVAL";

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Joins the caller's transaction so a notification is only sent if the change it describes commits. */
    @Transactional
    public void notify(AppUser user, Booking booking, String category, String title, String body) {
        Notification n = new Notification();
        n.setUser(user);
        n.setBooking(booking);
        n.setCategory(category);
        n.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        n.setBody(body.length() > 500 ? body.substring(0, 500) : body);
        n.setChannel("IN_APP");
        n.setDeliveryStatus("DELIVERED");
        n.setCreatedAt(LocalDateTime.now(clock));
        repository.save(n);
    }

    /** Notifies whoever made the booking (the host, when the rider is a guest). */
    @Transactional
    public void notifyBooker(Booking booking, String category, String title, String body) {
        notify(booking.getBookedBy(), booking, category, title, body);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> forUser(Long userId, int limit) {
        return repository.findForUser(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100))).stream()
                .map(NotificationDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, LocalDateTime.now(clock));
    }
}
