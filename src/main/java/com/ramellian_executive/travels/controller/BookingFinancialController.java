package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.model.BookingFinancialSummary;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.service.BookingFinancialService;
import com.ramellian_executive.travels.service.OverstayChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/financials")
@RequiredArgsConstructor
@Tag(name = "Financial Records", description = "Per-booking financial ledger and platform profit (ADMIN only)")
public class BookingFinancialController {

    private final BookingFinancialService bookingFinancialService;
    private final OverstayChargeService overstayChargeService;
    private final BookingRepository bookingRepository;

    @Operation(summary = "Full financial breakdown for a single booking — all charges and disbursements")
    @GetMapping("/bookings/{bookingId}")
    public BookingFinancialSummary getForBooking(@PathVariable UUID bookingId) {
        return bookingFinancialService.getForBooking(bookingId);
    }

    @Operation(summary = "List all bookings where at least one charge is paid but owner not yet fully disbursed")
    @GetMapping("/unsettled")
    public List<BookingFinancialSummary> unsettled() {
        return bookingFinancialService.listUnsettled();
    }

    @Operation(summary = "Platform-wide profit and liability summary")
    @GetMapping("/platform-profit")
    public BookingFinancialService.PlatformProfitSummary platformProfit() {
        return bookingFinancialService.platformProfit();
    }

    @Operation(summary = "Preview overstay charge for a booking given a hypothetical return time")
    @GetMapping("/bookings/{bookingId}/overstay-preview")
    public OverstayChargeService.OverstaySummary overstayPreview(
            @PathVariable UUID bookingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime returnAt) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        OverstayChargeService.OverstaySummary preview = overstayChargeService.preview(booking, returnAt);
        if (preview == null) {
            throw new ResponseStatusException(HttpStatus.OK,
                    "No overstay charge — vehicle is within agreed time or grace period");
        }
        return preview;
    }
}
