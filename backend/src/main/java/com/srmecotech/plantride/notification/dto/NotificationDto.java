package com.srmecotech.plantride.notification.dto;

import com.srmecotech.plantride.notification.Notification;

import java.time.LocalDateTime;

public record NotificationDto(Long id, String category, String title, String body, Long bookingId,
                              LocalDateTime createdAt, boolean read) {

    public static NotificationDto of(Notification n) {
        return new NotificationDto(n.getId(), n.getCategory(), n.getTitle(), n.getBody(),
                n.getBooking() == null ? null : n.getBooking().getId(), n.getCreatedAt(), n.getReadAt() != null);
    }
}
