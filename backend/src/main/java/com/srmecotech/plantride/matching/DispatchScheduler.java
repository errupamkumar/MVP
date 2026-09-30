package com.srmecotech.plantride.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The 15-second sweep: scheduled rides coming due, and anyone still waiting for a cab. */
@Component
@ConditionalOnProperty(name = "plantride.dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class DispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchScheduler.class);

    private final DispatchService dispatchService;

    public DispatchScheduler(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(initialDelayString = "${plantride.dispatch.interval-ms:15000}",
            fixedDelayString = "${plantride.dispatch.interval-ms:15000}")
    public void sweep() {
        try {
            int assigned = dispatchService.dispatchPending();
            if (assigned > 0) {
                log.info("Dispatch sweep assigned {} booking(s)", assigned);
            }
        } catch (RuntimeException ex) {
            log.error("Dispatch sweep failed", ex);
        }
    }
}
