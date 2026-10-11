package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.service.BookingPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/booking-policies")
public class BookingPolicyController {

    private final BookingPolicyService bookingPolicyService;

    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<CancellationResponse> cancel(@PathVariable UUID bookingId,
                                                        Authentication authentication) {
        return ResponseEntity.ok(bookingPolicyService.cancel(bookingId, authentication));
    }

    @PostMapping("/bookings/{bookingId}/postpone")
    public ResponseEntity<BookingResponse> postpone(@PathVariable UUID bookingId,
                                                     @Valid @RequestBody PostponementRequest request,
                                                     Authentication authentication) {
        return ResponseEntity.ok(bookingPolicyService.postpone(bookingId, request, authentication));
    }

    @PutMapping("/platform")
    public ResponseEntity<BookingPolicyResponse> setPlatformPolicy(
            @Valid @RequestBody BookingPolicyRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingPolicyService.createOrUpdatePlatformPolicy(request, authentication));
    }

    @PutMapping("/owner")
    public ResponseEntity<BookingPolicyResponse> setOwnerPolicy(
            @Valid @RequestBody BookingPolicyRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(bookingPolicyService.createOrUpdateOwnerPolicy(request, authentication));
    }

    @GetMapping("/platform")
    public ResponseEntity<BookingPolicyResponse> getPlatformPolicy() {
        return ResponseEntity.ok(bookingPolicyService.getPlatformPolicy());
    }

    @GetMapping("/owner")
    public ResponseEntity<BookingPolicyResponse> getOwnerPolicy(Authentication authentication) {
        return ResponseEntity.ok(bookingPolicyService.getOwnerPolicy(authentication));
    }

    @GetMapping("/bookings/{bookingId}/effective")
    public ResponseEntity<BookingPolicyResponse> getEffectivePolicyForBooking(
            @PathVariable UUID bookingId,
            Authentication authentication) {
        return ResponseEntity.ok(bookingPolicyService.getEffectivePolicyForBooking(bookingId, authentication));
    }
}
