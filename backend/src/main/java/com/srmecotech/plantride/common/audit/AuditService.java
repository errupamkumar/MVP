package com.srmecotech.plantride.common.audit;

import com.srmecotech.plantride.common.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * "Who booked, who changed it, who approved and who overrode": every
 * state-changing action writes one line here, inside the caller's
 * transaction, so an action and its audit line commit or roll back together.
 */
@Service
public class AuditService {

    private static final int MAX_DETAILS = 1000;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuthenticatedUser actor, String action, String entityType, Long entityId, String details) {
        write(actor == null ? null : actor.id(), actor == null ? "system" : actor.fullName(),
                action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(String action, String entityType, Long entityId, String details) {
        write(null, "system", action, entityType, entityId, details);
    }

    private void write(Long actorId, String actorName, String action, String entityType, Long entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actorId);
        entry.setActorName(actorName);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details);
        entry.setCreatedAt(LocalDateTime.now(clock));
        repository.save(entry);
    }
}
