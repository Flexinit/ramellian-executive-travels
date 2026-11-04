package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.entity.PaymentTransactionType;
import com.ramellian_executive.travels.model.PaymentTransactionResponse;
import com.ramellian_executive.travels.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payment-transactions")
@Tag(name = "Payment Transaction Ledger", description = "Unified queryable ledger of all M-Pesa and PesaPal transactions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasAuthority('PAYMENT_MANAGE')")
public class PaymentTransactionController {

    private final PaymentTransactionService service;

    @GetMapping
    @Operation(summary = "Search payment transactions",
               description = "Filter by type (MPESA_STK, MPESA_SPLIT_STK, MPESA_B2C, PESAPAL_CARD, PESAPAL_B2C), status, phone, bookingId, and date range")
    public ResponseEntity<Page<PaymentTransactionResponse>> search(
            @RequestParam(required = false) PaymentTransactionType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.search(type, status, phone, bookingId, from, to, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single payment transaction by ID")
    public ResponseEntity<PaymentTransactionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }
}
