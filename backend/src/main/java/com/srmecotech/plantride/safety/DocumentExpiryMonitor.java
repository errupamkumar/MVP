package com.srmecotech.plantride.safety;

import com.srmecotech.plantride.common.audit.AuditService;
import com.srmecotech.plantride.common.security.AuthenticatedUser;
import com.srmecotech.plantride.masterdata.ComplianceService;
import com.srmecotech.plantride.masterdata.Driver;
import com.srmecotech.plantride.masterdata.DriverRepository;
import com.srmecotech.plantride.masterdata.Vehicle;
import com.srmecotech.plantride.masterdata.VehicleRepository;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocState;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "Alerts before it lapses, a block after": raises one alert per document
 * per band (30, 7 and 1 days before expiry, then expired). Runs at startup
 * and every six hours.
 */
@Component
public class DocumentExpiryMonitor {

    private static final Logger log = LoggerFactory.getLogger(DocumentExpiryMonitor.class);
    private static final int ONE_YEAR_MINUTES = 60 * 24 * 365;

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final ComplianceService complianceService;
    private final AlertService alertService;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    public DocumentExpiryMonitor(VehicleRepository vehicleRepository, DriverRepository driverRepository,
                                 ComplianceService complianceService, AlertService alertService,
                                 AuditService auditService, PlatformTransactionManager transactionManager) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.complianceService = complianceService;
        this.alertService = alertService;
        this.auditService = auditService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        scan();
    }

    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduled() {
        scan();
    }

    /** @return alerts raised in this pass */
    public int scan() {
        Integer raised = tx.execute(status -> {
            int count = 0;
            for (Vehicle v : vehicleRepository.findAllForBoard()) {
                for (DocumentStatus doc : complianceService.vehicleDocuments(v)) {
                    if (doc.state() == DocState.VALID) {
                        continue;
                    }
                    String who = v.getCode();
                    String tail = doc.state() == DocState.EXPIRED ? " · blocked from dispatch"
                            : v.getVendor() != null ? " · vendor notified" : "";
                    count += raise("VEHICLE:" + v.getId(), doc, who + " " + ComplianceService.inSentence(doc.document()) + " "
                            + doc.label() + tail, v) ? 1 : 0;
                }
            }
            for (Driver d : driverRepository.findAllWithUser()) {
                for (DocumentStatus doc : complianceService.driverDocuments(d)) {
                    if (doc.state() == DocState.VALID) {
                        continue;
                    }
                    String who = d.getUser().getFullName() + " (" + d.getDriverCode() + ")";
                    String tail = doc.state() == DocState.EXPIRED ? " · blocked from duty" : "";
                    count += raise("DRIVER:" + d.getId(), doc, who + " " + ComplianceService.inSentence(doc.document()) + " "
                            + doc.label() + tail, null) ? 1 : 0;
                }
            }
            return count;
        });
        if (raised != null && raised > 0) {
            log.info("Document expiry scan raised {} alert(s)", raised);
        }
        return raised == null ? 0 : raised;
    }

    public void recordReset(AuthenticatedUser actor) {
        tx.executeWithoutResult(status -> auditService.record(actor, "DEMO_RESET", "SYSTEM", null,
                "Demo data reloaded by " + actor.fullName()));
    }

    private boolean raise(String subject, DocumentStatus doc, String message, Vehicle vehicle) {
        String band;
        AlertSeverity severity;
        if (doc.state() == DocState.EXPIRED) {
            band = "EXPIRED";
            severity = AlertSeverity.CRITICAL;
        } else if (doc.daysLeft() <= 1) {
            band = "D1";
            severity = AlertSeverity.HIGH;
        } else if (doc.daysLeft() <= 7) {
            band = "D7";
            severity = AlertSeverity.MEDIUM;
        } else {
            band = "D30";
            severity = AlertSeverity.LOW;
        }
        String key = "DOC:" + subject + ":" + doc.document().toUpperCase().replace(' ', '_') + ":" + band;
        return alertService.raise(AlertType.DOC_EXPIRY, severity, message)
                .vehicle(vehicle)
                .dedupe(key.length() > 100 ? key.substring(0, 100) : key, ONE_YEAR_MINUTES)
                .save()
                .isPresent();
    }
}
