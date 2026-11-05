package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.model.SplitPaymentRequest;
import com.ramellian_executive.travels.model.SplitPaymentResponse;
import com.ramellian_executive.travels.service.DarajaClient;
import com.ramellian_executive.travels.service.DisbursementService;
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

@RestController
@RequestMapping("/api/v1/payments/mpesa")
@RequiredArgsConstructor
@Tag(name = "M-Pesa Payments", description = "Daraja STK Push pay-in and B2C callbacks")
public class MpesaController {

    private static final Logger log = LoggerFactory.getLogger(MpesaController.class);

    private final MpesaPaymentService mpesaPaymentService;
    private final SplitPaymentService splitPaymentService;
    private final DisbursementService disbursementService;
    private final DarajaClient darajaClient;

    @Operation(summary = "Initiate M-Pesa STK Push for a booking", operationId = "mpesaCheckout")
    @PostMapping("/bookings/{bookingId}/checkout")
    public PaymentCheckoutResponse checkout(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) String phone,
            Authentication authentication) {
        return mpesaPaymentService.checkout(bookingId, phone, authentication);
    }

    @Operation(summary = "Retry M-Pesa STK Push for a PENDING or FAILED payment", operationId = "mpesaRetry")
    @PostMapping("/bookings/{bookingId}/retry")
    public PaymentCheckoutResponse retry(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) String phone,
            Authentication authentication) {
        return mpesaPaymentService.retryStkPush(bookingId, phone, authentication);
    }

    @Operation(summary = "Get M-Pesa payment status for a booking")
    @GetMapping("/bookings/{bookingId}/status")
    public PaymentStatusResponse status(@PathVariable UUID bookingId, Authentication authentication) {
        return mpesaPaymentService.getStatus(bookingId, authentication);
    }

    /**
     * Safaricom posts the STK Push result here after the customer enters their PIN.
     * Must be publicly accessible (no auth).
     * Split contributions are matched first; falls through to regular payment if unmatched.
     */
    @Operation(summary = "STK Push callback — called by Safaricom (no auth required)")
    @PostMapping("/stk/callback")
    public Map<String, String> stkCallback(@RequestBody Map<String, Object> payload) {
        log.info("STK callback received: {}", payload);
        try {
            boolean handled = splitPaymentService.handleStkCallback(payload);
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
     * Safaricom posts here after a customer completes a Paybill/Till payment (C2B).
     * BillRefNumber should be the booking UUID.
     */
    @Operation(summary = "C2B confirmation — called by Safaricom after Paybill payment (no auth required)",
               operationId = "mpesaC2bConfirmation")
    @PostMapping("/c2b/confirmation")
    public Map<String, String> c2bConfirmation(@RequestBody Map<String, Object> payload) {
        log.info("C2B confirmation received: {}", payload);
        mpesaPaymentService.handleC2bConfirmation(payload);
        return Map.of("ResultCode", "0", "ResultDesc", "Accepted");
    }

    /**
     * Safaricom posts here before confirming a C2B payment (optional validation).
     * We always accept — business logic is handled in the confirmation.
     */
    @Operation(summary = "C2B validation — called by Safaricom before confirming Paybill payment (no auth required)",
               operationId = "mpesaC2bValidation")
    @PostMapping("/c2b/validation")
    public Map<String, String> c2bValidation(@RequestBody Map<String, Object> payload) {
        log.info("C2B validation received: {}", payload);
        return Map.of("ResultCode", "0", "ResultDesc", "Accepted");
    }

    /**
     * Admin endpoint to register/refresh C2B URLs with Safaricom.
     * Call this once per environment after deployment.
     */
    @Operation(summary = "Register C2B URLs with Safaricom (admin only)", operationId = "registerC2bUrls")
    @PostMapping("/c2b/register")
    public Map<String, String> registerC2bUrls() {
        darajaClient.registerC2bUrls();
        return Map.of("status", "C2B URLs registered", "shortcode", darajaClient.getShortcode());
    }

    /**
     * Safaricom posts the B2C result here after a disbursement is processed.
     * Must be publicly accessible (no auth).
     */
    @Operation(summary = "B2C result callback — called by Safaricom (no auth required)")
    @PostMapping("/b2c/result")
    public Map<String, String> b2cResult(@RequestBody Map<String, Object> payload) {
        log.info("B2C result callback received: {}", payload);
        disbursementService.handleB2cCallback(payload);
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
