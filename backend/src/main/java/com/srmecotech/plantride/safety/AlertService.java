package com.srmecotech.plantride.safety;

import com.srmecotech.plantride.booking.Booking;
import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.error.BusinessRuleException;
import com.srmecotech.plantride.common.error.NotFoundException;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.identity.AppUser;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.safety.dto.AlertDto;
import com.srmecotech.plantride.trip.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class AlertService {

    private final AlertRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public AlertService(AlertRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Builder-style raise, so callers name only what they have. */
    public RaiseBuilder raise(AlertType type, AlertSeverity severity, String message) {
        return new RaiseBuilder(type, severity, message);
    }

    @Transactional(readOnly = true)
    public List<AlertDto> unresolved() {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByStatuses(EnumSet.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED)).stream()
                .sorted(Comparator.comparing(Alert::getStatus)
                        .thenComparing(Alert::getSeverity)
                        .thenComparing(Alert::getCreatedAt, Comparator.reverseOrder()))
                .map(a -> AlertDto.of(a, now))
                .toList();
    }

    @Transactional
    public AlertDto acknowledge(Long alertId, AuthenticatedUser actor) {
        Alert alert = repository.findById(alertId).orElseThrow(() -> new NotFoundException("Alert", alertId));
        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new BusinessRuleException("ALERT_RESOLVED", "This alert is already resolved.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (alert.getStatus() == AlertStatus.OPEN) {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(now);
            alert.setAcknowledgedBy(actor.fullName());
            auditService.record(actor, "ALERT_ACKNOWLEDGED", "ALERT", alert.getId(), alert.getMessage());
        }
        return AlertDto.of(alert, now);
    }

    @Transactional
    public AlertDto resolve(Long alertId, AuthenticatedUser actor) {
        Alert alert = repository.findById(alertId).orElseThrow(() -> new NotFoundException("Alert", alertId));
        LocalDateTime now = LocalDateTime.now(clock);
        if (alert.getStatus() != AlertStatus.RESOLVED) {
            if (alert.getAcknowledgedAt() == null) {
                alert.setAcknowledgedAt(now);
                alert.setAcknowledgedBy(actor.fullName());
            }
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(now);
            alert.setResolvedBy(actor.fullName());
            auditService.record(actor, "ALERT_RESOLVED", "ALERT", alert.getId(), alert.getMessage());
        }
        return AlertDto.of(alert, now);
    }

    public final class RaiseBuilder {
        private final Alert alert = new Alert();
        private Integer suppressMinutes;

        private RaiseBuilder(AlertType type, AlertSeverity severity, String message) {
            alert.setType(type);
            alert.setSeverity(severity);
            alert.setStatus(AlertStatus.OPEN);
            alert.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        }

        public RaiseBuilder vehicle(Vehicle vehicle) {
            alert.setVehicle(vehicle);
            return this;
        }

        public RaiseBuilder trip(Trip trip) {
            alert.setTrip(trip);
            return this;
        }

        public RaiseBuilder booking(Booking booking) {
            alert.setBooking(booking);
            return this;
        }

        public RaiseBuilder raisedBy(AppUser user) {
            alert.setRaisedBy(user);
            return this;
        }

        public RaiseBuilder at(Double latitude, Double longitude) {
            alert.setLatitude(latitude);
            alert.setLongitude(longitude);
            return this;
        }

        /**
         * Skip raising if an alert with this key is unresolved, or was raised
         * within the last {@code minutes}.
         */
        public RaiseBuilder dedupe(String key, int minutes) {
            alert.setDedupeKey(key);
            this.suppressMinutes = minutes;
            return this;
        }

        /**
         * Joins the caller's transaction when there is one (the builder is not
         * a Spring bean, so it cannot start its own).
         *
         * @return the saved alert, or empty when suppressed as a duplicate
         */
        public Optional<Alert> save() {
            LocalDateTime now = LocalDateTime.now(clock);
            if (alert.getDedupeKey() != null && suppressMinutes != null
                    && repository.existsRecent(alert.getDedupeKey(), now.minusMinutes(suppressMinutes))) {
                return Optional.empty();
            }
            alert.setCreatedAt(now);
            return Optional.of(repository.save(alert));
        }
    }
}
