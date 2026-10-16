package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.model.ChangeVehicleRequest;
import com.ramellian_executive.travels.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;

    @PostMapping("/create")
    public BookingResponse create(@Valid @RequestBody BookingRequest request,
                                  Authentication authentication) {
        return bookingService.create(request, authentication);
    }

    @GetMapping("/mine")
    public List<BookingResponse> mine(Authentication authentication) {
        return bookingService.mine(authentication);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id, Authentication authentication) {
        return bookingService.get(id, authentication);
    }

    @GetMapping("/owner")
    public List<BookingResponse> forOwner(Authentication authentication) {
        return bookingService.forOwner(authentication);
    }

    @GetMapping("/reports/mine")
    public BookingReportResponse renterReport(Authentication authentication) {
        return bookingService.renterReport(authentication);
    }

    @GetMapping("/reports/owner")
    public BookingReportResponse ownerReport(Authentication authentication) {
        return bookingService.ownerReport(authentication);
    }

    @GetMapping("/reports/admin")
    public BookingReportResponse platformReport() {
        return bookingService.platformReport();
    }

    @PutMapping("/{id}/status")
    public BookingResponse changeStatus(@PathVariable UUID id,
                                        @Valid @RequestBody BookingStatusRequest request,
                                        Authentication authentication) {
        return bookingService.changeStatus(id, request, authentication);
    }

    @PutMapping("/{id}/chauffeur")
    public BookingResponse assignChauffeur(@PathVariable UUID id,
                                           @Valid @RequestBody ChauffeurAssignmentRequest request,
                                           Authentication authentication) {
        return bookingService.assignChauffeur(id, request.chauffeurId(), authentication);
    }

    @PostMapping("/create-batch")
    public ResponseEntity<BookingGroupResponse> createBatch(
            @RequestBody @Valid BatchBookingRequest request,
            Authentication authentication) {
        return ResponseEntity.status(201).body(bookingService.createBatch(request, authentication));
    }

    @PostMapping("/quote")
    public ResponseEntity<BookingQuoteResponse> quote(@RequestBody @Valid BookingQuoteRequest request) {
        return ResponseEntity.ok(bookingService.quote(request));
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<BookingResponse> recordReturn(@PathVariable UUID id,
                                                        @Valid @RequestBody BookingReturnRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(bookingService.recordReturn(id, request, authentication));
    }

    @PutMapping("/{id}/change-vehicle")
    public ResponseEntity<BookingResponse> changeVehicle(@PathVariable UUID id,
                                                         @Valid @RequestBody ChangeVehicleRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(bookingService.changeVehicle(id, request, authentication));
    }
}
