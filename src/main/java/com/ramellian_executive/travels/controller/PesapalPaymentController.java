package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.service.PesapalPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/pesapal")
public class PesapalPaymentController {
    private final PesapalPaymentService service;

    @PostMapping("/bookings/{bookingId}/checkout")
    public PaymentCheckoutResponse checkout(@PathVariable UUID bookingId,
                                            Authentication authentication) {
        return service.checkout(bookingId, authentication);
    }

    @GetMapping("/bookings/{bookingId}/status")
    public PaymentStatusResponse status(@PathVariable UUID bookingId,
                                        Authentication authentication) {
        return service.status(bookingId, authentication);
    }

    @RequestMapping(value = "/ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, String> ipn(@RequestParam String OrderTrackingId,
                                   @RequestParam String OrderMerchantReference) {
        service.processCallback(OrderTrackingId, OrderMerchantReference);
        return Map.of("status", "received");
    }

    @PostMapping("/booking-groups/{groupId}/checkout")
    public ResponseEntity<PaymentCheckoutResponse> checkoutGroup(
            @PathVariable UUID groupId,
            Authentication authentication) {
        return ResponseEntity.ok(service.checkoutGroup(groupId, authentication));
    }

    @PostMapping("/bookings/{bookingId}/overstay-checkout")
    public ResponseEntity<PaymentCheckoutResponse> checkoutOverstay(
            @PathVariable UUID bookingId,
            Authentication authentication) {
        return ResponseEntity.ok(service.checkoutOverstay(bookingId, authentication));
    }
}
