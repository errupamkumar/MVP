package com.srmecotech.plantride.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DispatchTrigger {

    private static final Logger log = LoggerFactory.getLogger(DispatchTrigger.class);

    private final DispatchService dispatchService;

    public DispatchTrigger(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /**
     * Runs after the publishing transaction commits, so the freed cab or new
     * demand is visible. Never throws: the caller's change is already committed,
     * and an exception here would reach the client as a 500 for an action that
     * succeeded (a driver would then retry sign-on and be told "already on
     * duty"). The 15-second sweep retries anything missed.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDispatchRequested(DispatchRequestedEvent event) {
        try {
            int assigned = dispatchService.dispatchPending();
            if (assigned > 0) {
                log.info("Dispatch after '{}' assigned {} waiting booking(s)", event.reason(), assigned);
            }
        } catch (RuntimeException ex) {
            log.error("Dispatch after '{}' failed; the scheduled sweep will retry", event.reason(), ex);
        }
    }
}
