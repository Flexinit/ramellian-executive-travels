package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.B2cCallbackResultResponse;
import com.ramellian_executive.travels.model.C2bCallbackResultResponse;
import com.ramellian_executive.travels.model.StkCallbackResultResponse;
import com.ramellian_executive.travels.service.DisbursementService;
import com.ramellian_executive.travels.service.MpesaPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/mpesa-callbacks")
@PreAuthorize("hasAuthority('PAYMENT_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class MpesaCallbackController {

    private final MpesaPaymentService mpesaPaymentService;
    private final DisbursementService disbursementService;

    @GetMapping("/stk")
    public List<StkCallbackResultResponse> listStkCallbacks(
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) UUID bookingId) {
        return mpesaPaymentService.listStkCallbacks(paymentId, bookingId);
    }

    @GetMapping("/c2b")
    public List<C2bCallbackResultResponse> listC2bCallbacks(
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) UUID bookingId) {
        return mpesaPaymentService.listC2bCallbacks(paymentId, bookingId);
    }

    @GetMapping("/b2c")
    public List<B2cCallbackResultResponse> listB2cCallbacks(
            @RequestParam(required = false) UUID disbursementId) {
        return disbursementService.listB2cCallbacks(disbursementId);
    }
}
