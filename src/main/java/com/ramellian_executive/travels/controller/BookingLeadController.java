package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.BookingLeadRequest;
import com.ramellian_executive.travels.model.BookingLeadResponse;
import com.ramellian_executive.travels.service.BookingLeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/booking-leads")
public class BookingLeadController {
    private final BookingLeadService bookingLeadService;

    @PostMapping
    public ResponseEntity<BookingLeadResponse> create(@RequestBody @Valid BookingLeadRequest request) {
        return ResponseEntity.status(201).body(bookingLeadService.create(request));
    }
}
