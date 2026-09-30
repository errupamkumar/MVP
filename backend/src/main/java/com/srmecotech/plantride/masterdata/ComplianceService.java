package com.srmecotech.plantride.masterdata;

import com.srmecotech.plantride.common.settings.SettingsService;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocState;
import com.srmecotech.plantride.masterdata.dto.MasterDataDtos.DocumentStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Document validity for vehicles and drivers. An expired document is a
 * block, not a warning: the vehicle or driver cannot start a duty and is
 * never offered by the matching engine.
 */
@Service
public class ComplianceService {

    private static final DateTimeFormatter VALID_TO = DateTimeFormatter.ofPattern("MM/yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter EXPIRED_ON = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final Clock clock;
    private final SettingsService settings;

    public ComplianceService(Clock clock, SettingsService settings) {
        this.clock = clock;
        this.settings = settings;
    }

    public List<DocumentStatus> vehicleDocuments(Vehicle v) {
        int alertDays = alertDays();
        return List.of(
                status("Vehicle insurance", v.getInsurancePolicyNo(), v.getInsuranceExpiry(), alertDays),
                status("Fitness certificate", v.getRegistrationNo(), v.getFitnessExpiry(), alertDays),
                status("PUC certificate", v.getRegistrationNo(), v.getPucExpiry(), alertDays));
    }

    public List<DocumentStatus> driverDocuments(Driver d) {
        int alertDays = alertDays();
        return List.of(
                status("Driving licence", d.getLicenceNo(), d.getLicenceExpiry(), alertDays),
                status("Gate pass", d.getGatePassNo(), d.getGatePassExpiry(), alertDays));
    }

    public Optional<String> vehicleBlockReason(Vehicle v) {
        return vehicleDocuments(v).stream()
                .filter(doc -> doc.state() == DocState.EXPIRED)
                .findFirst()
                .map(doc -> v.getCode() + " " + inSentence(doc.document()) + " expired on "
                        + doc.expiresOn().format(EXPIRED_ON) + ". Dispatch is blocked.");
    }

    public Optional<String> driverBlockReason(Driver d) {
        return driverDocuments(d).stream()
                .filter(doc -> doc.state() == DocState.EXPIRED)
                .findFirst()
                .map(doc -> d.getUser().getFullName() + "'s " + inSentence(doc.document()) + " expired on "
                        + doc.expiresOn().format(EXPIRED_ON) + ". Duty is blocked until it is renewed.");
    }

    public DocumentStatus status(String document, String reference, LocalDate expiry, int alertDays) {
        LocalDate today = LocalDate.now(clock);
        long daysLeft = ChronoUnit.DAYS.between(today, expiry);
        DocState state;
        String label;
        if (daysLeft < 0) {
            state = DocState.EXPIRED;
            long ago = -daysLeft;
            label = ago == 1 ? "expired yesterday" : "expired " + ago + " days ago";
        } else if (daysLeft <= alertDays) {
            state = DocState.EXPIRING;
            label = daysLeft == 0 ? "expires today" : daysLeft == 1 ? "expires tomorrow" : "expires in " + daysLeft + " days";
        } else {
            state = DocState.VALID;
            label = "valid to " + expiry.format(VALID_TO);
        }
        return new DocumentStatus(document, reference, expiry, daysLeft, state, label);
    }

    /** "Vehicle insurance" -> "vehicle insurance", but "PUC certificate" keeps its acronym. */
    public static String inSentence(String document) {
        String first = document.split(" ", 2)[0];
        if (first.length() > 1 && first.equals(first.toUpperCase(Locale.ROOT))) {
            return document;
        }
        return Character.toLowerCase(document.charAt(0)) + document.substring(1);
    }

    private int alertDays() {
        return settings.getInt(SettingsService.DOC_EXPIRY_ALERT_DAYS, 30);
    }
}
