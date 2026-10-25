package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.entity.DisbursementStatus;
import com.ramellian_executive.travels.model.DisbursementResponse;
import com.ramellian_executive.travels.model.DisbursementTriggerRequest;
import com.ramellian_executive.travels.service.DisbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/disbursements")
@PreAuthorize("hasAuthority('PAYMENT_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class DisbursementController {

    private final DisbursementService disbursementService;

    @GetMapping
    public List<DisbursementResponse> list(@RequestParam(required = false) DisbursementStatus status) {
        return disbursementService.list(status);
    }

    @GetMapping("/{id}")
    public DisbursementResponse getById(@PathVariable UUID id) {
        return disbursementService.getById(id);
    }

    @GetMapping("/bookings/{bookingId}")
    public DisbursementResponse getByBooking(@PathVariable UUID bookingId) {
        return disbursementService.getByBooking(bookingId);
    }

    @PostMapping("/{id}/trigger")
    public DisbursementResponse trigger(@PathVariable UUID id,
                                        @RequestBody(required = false) DisbursementTriggerRequest request,
                                        Authentication authentication) {
        return disbursementService.trigger(id, request, authentication);
    }

    @PostMapping("/{id}/fail")
    public DisbursementResponse markFailed(@PathVariable UUID id,
                                           @RequestBody(required = false) DisbursementTriggerRequest request,
                                           Authentication authentication) {
        return disbursementService.markFailed(id, request, authentication);
    }

    @PostMapping("/{id}/sync")
    public DisbursementResponse sync(@PathVariable UUID id, Authentication authentication) {
        return disbursementService.syncStatus(id, authentication);
    }
}
