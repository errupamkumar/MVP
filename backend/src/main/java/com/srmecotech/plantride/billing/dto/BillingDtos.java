package com.srmecotech.plantride.billing.dto;

import java.math.BigDecimal;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {
    }

    public record BillingSummaryDto(
            String period,
            /** The current month is a draft until it closes. */
            boolean draft,
            String sharingRule,
            Totals totals,
            List<CostCentreCharge> costCentres,
            List<VendorReconciliation> vendors,
            ComplianceWatch compliance) {
    }

    public record Totals(long trips, long rides, BigDecimal gpsKm, BigDecimal amount, BigDecimal ridersPerTrip) {
    }

    public record CostCentreCharge(
            String code,
            String name,
            long trips,
            long rides,
            BigDecimal km,
            BigDecimal amount,
            BigDecimal budget,
            int budgetUsedPct,
            /** OK, WARN (80% or more used), OVER (100% or more used). */
            String budgetStatus) {
    }

    public record VendorReconciliation(
            String vendorCode,
            String vendorName,
            BigDecimal claimedKm,
            BigDecimal gpsKm,
            BigDecimal gapKm,
            BigDecimal gapPct,
            BigDecimal claimedAmount,
            BigDecimal gpsAmount,
            /** MATCHED, DISPUTE or NO_CLAIM. */
            String status) {
    }

    public record ComplianceWatch(
            int documentsExpiringSoon,
            int expiredDocuments,
            int blockedVehicles,
            int blockedDrivers,
            int fullyCompliantVehicles,
            int totalVehicles) {
    }
}
