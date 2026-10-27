package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.entity.Disbursement;
import com.ramellian_executive.travels.entity.DisbursementStatus;
import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.model.SplitPaymentRequest;
import com.ramellian_executive.travels.model.SplitPaymentResponse;
import com.ramellian_executive.travels.repository.DisbursementRepository;
import com.ramellian_executive.travels.service.DarajaClient;
import com.ramellian_executive.travels.service.MpesaPaymentService;
import com.ramellian_executive.travels.service.SplitPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/payments/mpesa")
@RequiredArgsConstructor
@Tag(name = "M-Pesa Payments", description = "Daraja STK Push pay-in and B2C callbacks")
public class MpesaController {

    private static final Logger log = LoggerFactory.getLogger(MpesaController.class);

    private final MpesaPaymentService mpesaPaymentService;
    private final SplitPaymentService splitPaymentService;
    private final DisbursementRepository disbursementRepository;

    @Operation(summary = "Initiate M-Pesa STK Push for a booking")
    @PostMapping("/bookings/{bookingId}/checkout")
    public PaymentCheckoutResponse checkout(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) String phone,
            Authentication authentication) {
        return mpesaPaymentService.checkout(bookingId, phone, authentication);
    }

    @Operation(summary = "Get M-Pesa payment status for a booking")
    @GetMapping("/bookings/{bookingId}/status")
    public PaymentStatusResponse status(@PathVariable UUID bookingId, Authentication authentication) {
        return mpesaPaymentService.getStatus(bookingId, authentication);
    }

    /**
     * Safaricom posts the STK Push result here after the customer enters their PIN.
     * Must be publicly accessible (no auth).
     */
    @Operation(summary = "STK Push callback — called by Safaricom (no auth required)")
    @PostMapping("/stk/callback")
    @SuppressWarnings("unchecked")
    public Map<String, String> stkCallback(@RequestBody Map<String, Object> payload) {
        log.info("STK callback received: {}", payload);
        try {
            Map<String, Object> body     = (Map<String, Object>) payload.get("Body");
            Map<String, Object> callback = (Map<String, Object>) body.get("stkCallback");
            String checkoutRequestId     = String.valueOf(callback.get("CheckoutRequestID"));
            int resultCode               = ((Number) callback.get("ResultCode")).intValue();

            List<Map<String, Object>> items = null;
            if (resultCode == 0) {
                Map<String, Object> meta = (Map<String, Object>) callback.get("CallbackMetadata");
                items = (List<Map<String, Object>>) meta.get("Item");
            }
            String receipt = items != null
                    ? DarajaClient.extractResultParam(items, "MpesaReceiptNumber") : null;

            // Route: split contribution takes priority over regular payment
            boolean handled = splitPaymentService.handleStkCallback(checkoutRequestId, resultCode, receipt);
            if (!handled) {
                mpesaPaymentService.handleStkCallback(payload);
            }
        } catch (Exception e) {
            log.error("Error routing STK callback", e);
        }
        return Map.of("ResultCode", "0", "ResultDesc", "Accepted");
    }

    // ── Split payment endpoints ────────────────────────────────────────────

    @Operation(summary = "Initiate a split payment across multiple M-Pesa payers")
    @PostMapping("/bookings/{bookingId}/split")
    public SplitPaymentResponse initiateSplit(@PathVariable UUID bookingId,
                                              @Valid @RequestBody SplitPaymentRequest request,
                                              Authentication authentication) {
        return splitPaymentService.initiate(bookingId, request, authentication);
    }

    @Operation(summary = "Get current status of a split payment for a booking")
    @GetMapping("/bookings/{bookingId}/split")
    public SplitPaymentResponse getSplitStatus(@PathVariable UUID bookingId) {
        return splitPaymentService.getStatus(bookingId);
    }

    @Operation(summary = "Retry STK Push for a specific contributor that failed or is pending")
    @PostMapping("/bookings/{bookingId}/split/contributions/{contributionId}/retry")
    public SplitPaymentResponse retrySplitContribution(@PathVariable UUID bookingId,
                                                        @PathVariable UUID contributionId,
                                                        Authentication authentication) {
        return splitPaymentService.retryContribution(bookingId, contributionId, authentication);
    }

    /**
     * Safaricom posts the B2C result here after a disbursement is processed.
     * Must be publicly accessible (no auth).
     */
    @Operation(summary = "B2C result callback — called by Safaricom (no auth required)")
    @PostMapping("/b2c/result")
    @SuppressWarnings("unchecked")
    public Map<String, String> b2cResult(@RequestBody Map<String, Object> payload) {
        log.info("B2C result callback received: {}", payload);
        try {
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            if (result == null) return Map.of("ResultCode", "0", "ResultDesc", "Accepted");

            int resultCode = ((Number) result.get("ResultCode")).intValue();
            String originatorId = String.valueOf(result.get("OriginatorConversationID"));
            String transactionId = String.valueOf(result.getOrDefault("TransactionID", ""));

            // OriginatorConversationID is set to disbursement.getId().toString() in DisbursementService
            UUID disbursementId = null;
            try { disbursementId = UUID.fromString(originatorId); } catch (Exception ignored) {}

            if (disbursementId != null) {
                final UUID finalId = disbursementId;
                disbursementRepository.findById(finalId).ifPresent(d -> {
                    if (resultCode == 0) {
                        d.setStatus(DisbursementStatus.COMPLETED);
                        d.setReference(transactionId.isBlank() ? d.getReference() : transactionId);
                        log.info("Disbursement {} marked COMPLETED via B2C callback", finalId);
                    } else {
                        d.setStatus(DisbursementStatus.FAILED);
                        log.warn("Disbursement {} marked FAILED via B2C callback — ResultCode={}", finalId, resultCode);
                    }
                    disbursementRepository.save(d);
                });
            }

            // Extract result parameters for logging
            try {
                Map<String, Object> params = (Map<String, Object>) result.get("ResultParameters");
                if (params != null) {
                    List<Map<String, Object>> paramList = (List<Map<String, Object>>) params.get("ResultParameter");
                    String receipt = DarajaClient.extractResultParam(paramList, "TransactionReceipt");
                    log.info("B2C result — originatorId={} resultCode={} receipt={}", originatorId, resultCode, receipt);
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.error("Error processing B2C result callback", e);
        }
        return Map.of("ResultCode", "0", "ResultDesc", "Accepted");
    }

    /**
     * Safaricom posts here if a B2C request times out in the queue.
     * Must be publicly accessible (no auth).
     */
    @Operation(summary = "B2C queue timeout callback — called by Safaricom (no auth required)")
    @PostMapping("/b2c/timeout")
    public Map<String, String> b2cTimeout(@RequestBody Map<String, Object> payload) {
        log.warn("B2C queue timeout received: {}", payload);
        return Map.of("ResultCode", "0", "ResultDesc", "Accepted");
    }
}
