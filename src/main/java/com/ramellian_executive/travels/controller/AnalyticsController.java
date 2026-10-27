package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.analytics.*;
import com.ramellian_executive.travels.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public PlatformSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return analyticsService.summary(from, to);
    }

    @GetMapping("/earnings/daily")
    public List<DailyEarningResponse> dailyEarnings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().minusDays(29);
        if (to == null) to = LocalDate.now();
        return analyticsService.dailyEarnings(from, to);
    }

    @GetMapping("/bookings")
    public BookingAnalyticsResponse bookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return analyticsService.bookingAnalytics(from, to);
    }

    @GetMapping("/vehicles/top")
    public List<TopVehicleResponse> topVehicles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        if (from == null) from = LocalDate.now().minusMonths(3);
        if (to == null) to = LocalDate.now();
        return analyticsService.topVehicles(from, to, Math.min(limit, 50));
    }

    @GetMapping("/owners/top")
    public List<TopOwnerResponse> topOwners(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        if (from == null) from = LocalDate.now().minusMonths(3);
        if (to == null) to = LocalDate.now();
        return analyticsService.topOwners(from, to, Math.min(limit, 50));
    }
}
