package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.entity.RefundStatus;
import com.ramellian_executive.travels.model.RefundRequest;
import com.ramellian_executive.travels.model.RefundResponse;
import com.ramellian_executive.travels.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@Tag(name = "Refunds", description = "Pesapal refund management (ADMIN only)")
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "Initiate a Pesapal refund for a booking's rental payment")
    @PostMapping("/booking/{bookingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse requestRefund(@PathVariable UUID bookingId,
                                        @RequestBody(required = false) RefundRequest request,
                                        Authentication authentication) {
        return refundService.requestRefund(bookingId, request, authentication);
    }

    @Operation(summary = "List all refunds, optionally filtered by status")
    @GetMapping
    public List<RefundResponse> list(@RequestParam(required = false) RefundStatus status) {
        return refundService.list(status);
    }

    @Operation(summary = "Get a refund by its ID")
    @GetMapping("/{id}")
    public RefundResponse getById(@PathVariable UUID id) {
        return refundService.getById(id);
    }

    @Operation(summary = "Get the refund for a specific booking")
    @GetMapping("/booking/{bookingId}")
    public RefundResponse getByBooking(@PathVariable UUID bookingId) {
        return refundService.getByBooking(bookingId);
    }

    @Operation(summary = "Manually mark a refund as completed")
    @PostMapping("/{id}/complete")
    public RefundResponse markCompleted(@PathVariable UUID id, Authentication authentication) {
        return refundService.markCompleted(id, authentication);
    }

    @Operation(summary = "Manually mark a refund as failed")
    @PostMapping("/{id}/fail")
    public RefundResponse markFailed(@PathVariable UUID id, Authentication authentication) {
        return refundService.markFailed(id, authentication);
    }
}
