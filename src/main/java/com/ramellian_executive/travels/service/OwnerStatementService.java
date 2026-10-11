package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.Payment;
import com.ramellian_executive.travels.entity.PaymentStatus;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.model.OwnerStatementResponse;
import com.ramellian_executive.travels.model.OwnerStatementResponse.*;
import com.ramellian_executive.travels.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerStatementService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final PaymentRepository paymentRepository;

    @Value("${app.email.currency:KES}")
    private String currency;

    @Transactional(readOnly = true)
    public OwnerStatementResponse statement(Authentication authentication,
                                            UUID vehicleId,
                                            ZonedDateTime from,
                                            ZonedDateTime to) {
        String ownerEmail = authentication.getName();
        List<Payment> payments = paymentRepository.findOwnerPayments(
                ownerEmail, PaymentStatus.SUCCESSFUL, vehicleId, from, to);

        // ── Summary ──────────────────────────────────────────────────────────
        double totalGross = payments.stream().mapToDouble(Payment::getAmount).sum();
        double totalPlatform = payments.stream().mapToDouble(p -> p.getPlatformFee() != null ? p.getPlatformFee() : 0.0).sum();
        double totalTxFee = payments.stream().mapToDouble(p -> p.getTransactionFee() != null ? p.getTransactionFee() : 0.0).sum();
        double totalPayout = payments.stream().mapToDouble(p -> p.getOwnerPayout() != null ? p.getOwnerPayout() : 0.0).sum();

        Summary summary = new Summary(payments.size(), totalGross, totalPlatform, totalTxFee, totalPayout);

        // ── Monthly breakdown (for line/bar chart) ───────────────────────────
        Map<String, List<Payment>> byMonth = new LinkedHashMap<>();
        payments.stream()
                .sorted(Comparator.comparing(p -> p.getCreatedAt()))
                .forEach(p -> {
                    String key = p.getCreatedAt().getYear() + "-"
                            + String.format("%02d", p.getCreatedAt().getMonthValue());
                    byMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
                });

        List<MonthlyBreakdown> monthly = byMonth.entrySet().stream().map(e -> {
            String[] parts = e.getKey().split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            List<Payment> mp = e.getValue();
            double gross = mp.stream().mapToDouble(Payment::getAmount).sum();
            double payout = mp.stream().mapToDouble(p -> p.getOwnerPayout() != null ? p.getOwnerPayout() : 0.0).sum();
            String label = mp.get(0).getCreatedAt().format(MONTH_FMT);
            return new MonthlyBreakdown(year, month, label, mp.size(), gross, payout);
        }).toList();

        // ── Vehicle breakdown (for pie/bar chart) ────────────────────────────
        Map<UUID, List<Payment>> byVehicle = payments.stream()
                .collect(Collectors.groupingBy(p -> p.getBooking().getVehicle().getId()));

        List<VehicleBreakdown> vehicleBreakdown = byVehicle.entrySet().stream().map(e -> {
            List<Payment> vp = e.getValue();
            Vehicle v = vp.get(0).getBooking().getVehicle();
            String title = vehicleTitle(v);
            double gross = vp.stream().mapToDouble(Payment::getAmount).sum();
            double payout = vp.stream().mapToDouble(p -> p.getOwnerPayout() != null ? p.getOwnerPayout() : 0.0).sum();
            return new VehicleBreakdown(e.getKey(), title, vp.size(), gross, payout);
        }).sorted(Comparator.comparingDouble(VehicleBreakdown::grossRevenue).reversed()).toList();

        // ── Statement entries (table) ─────────────────────────────────────────
        List<StatementEntry> entries = payments.stream().map(p -> {
            Booking b = p.getBooking();
            Vehicle v = b.getVehicle();
            com.ramellian_executive.travels.entity.User renter = b.getUser();
            long totalHours = java.time.Duration.between(b.getStartTime(), b.getEndTime()).toHours();
            long days = Math.max(1, (totalHours + 23) / 24);
            String renterName = joinName(renter.getFirstName(), renter.getLastName());
            String receiptNo = p.getReceiptFileName() != null
                    ? "RCPT-" + p.getReceiptFileName().replace("RCPT-", "").replace(".pdf", "").toUpperCase()
                    : null;
            return new StatementEntry(
                    b.getId(), p.getId(), vehicleTitle(v), renterName, renter.getEmailAddress(),
                    b.getStartTime(), b.getEndTime(), days,
                    p.getAmount() - (p.getTransactionFee() != null ? p.getTransactionFee() : 0.0),
                    p.getPlatformFee() != null ? p.getPlatformFee() : 0.0,
                    p.getTransactionFee() != null ? p.getTransactionFee() : 0.0,
                    p.getOwnerPayout() != null ? p.getOwnerPayout() : 0.0,
                    p.getPaymentMethod(),
                    p.getCreatedAt(),
                    receiptNo
            );
        }).toList();

        return new OwnerStatementResponse(ZonedDateTime.now(), from, to,
                summary, monthly, vehicleBreakdown, entries);
    }

    private static String vehicleTitle(Vehicle v) {
        if (v == null) return "Vehicle";
        String t = v.getModel() != null ? v.getModel() : "Vehicle";
        if (v.getRegistrationNumber() != null && !v.getRegistrationNumber().isBlank())
            t += " (" + v.getRegistrationNumber() + ")";
        return t;
    }

    private static String joinName(String first, String last) {
        String n = java.util.stream.Stream.of(first, last)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return n.isBlank() ? "—" : n;
    }
}
