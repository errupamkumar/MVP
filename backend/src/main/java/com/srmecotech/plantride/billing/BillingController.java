package com.srmecotech.plantride.billing;

import com.srmecotech.plantride.billing.dto.BillingDtos.BillingSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/billing")
@Tag(name = "6. Billing (admin)")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Charges by cost centre, vendor bill vs GPS, and compliance watch for a month")
    public BillingSummaryDto summary(
            @Parameter(description = "Billing month yyyy-MM; defaults to the current month")
            @RequestParam(required = false) String period) {
        return billingService.summary(period);
    }

    // No produces="text/csv": that made Spring answer 406 to any client not sending Accept: text/csv
    // (the app's JSON Axios instance included). The content type is set on the response instead.
    @GetMapping("/export")
    @Operation(summary = "Ride-level CSV for the month-end ERP (SAP) import")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String period) {
        byte[] body = billingService.exportCsv(period).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(billingService.csvFileName(period)).build().toString())
                .body(body);
    }
}
