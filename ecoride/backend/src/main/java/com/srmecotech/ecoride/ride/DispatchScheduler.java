package com.srmecotech.ecoride.ride;

import com.srmecotech.ecoride.common.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the dispatch sweep on one dedicated thread: every second, and right away when a ride
 * is requested or a driver declines (a "kick"), so the first offer reaches a driver in
 * well under a second.
 */
@Component
public class DispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchScheduler.class);

    private final DispatchService dispatch;
    private final AppProperties.Dispatch config;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dispatch");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean kickQueued = new AtomicBoolean(false);
    private volatile boolean paused;

    public DispatchScheduler(DispatchService dispatch, AppProperties properties) {
        this.dispatch = dispatch;
        this.config = properties.dispatch();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (config.enabled()) {
            executor.scheduleWithFixedDelay(this::run, config.intervalMs(), config.intervalMs(), TimeUnit.MILLISECONDS);
            log.info("Dispatcher running every {} ms", config.intervalMs());
        }
    }

    /** Sweep as soon as the current transaction commits (or now, outside a transaction). */
    public void kick() {
        if (!config.enabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitKick();
                }
            });
        } else {
            submitKick();
        }
    }

    /** Stops dispatching while the demo data is being rebuilt. */
    public void pause(boolean pause) {
        this.paused = pause;
    }

    private void submitKick() {
        if (kickQueued.compareAndSet(false, true)) {
            executor.execute(() -> {
                kickQueued.set(false);
                run();
            });
        }
    }

    private void run() {
        if (paused) {
            return;
        }
        try {
            dispatch.sweep();
        } catch (RuntimeException ex) {
            log.warn("Dispatch sweep failed: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}
