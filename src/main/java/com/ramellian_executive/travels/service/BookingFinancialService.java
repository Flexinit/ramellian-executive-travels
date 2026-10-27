package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.BookingFinancialSummary;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.DisbursementRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingFinancialService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final DisbursementRepository disbursementRepository;

    public BookingFinancialSummary getForBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        List<Payment> payments = paymentRepository.findAllByBookingIdOrderByCreatedAtAsc(bookingId);
        if (payments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No payment records found for this booking");
        }

        List<UUID> paymentIds = payments.stream().map(Payment::getId).toList();
        Map<UUID, Disbursement> disbursementMap = disbursementRepository
                .findAllByPaymentIdIn(paymentIds).stream()
                .collect(Collectors.toMap(d -> d.getPayment().getId(), d -> d));

        return BookingFinancialSummary.from(booking, payments, disbursementMap);
    }

    /** All bookings where at least one charge is paid but not yet fully disbursed to owner. */
    public List<BookingFinancialSummary> listUnsettled() {
        // Load all successful payments, group by booking, build summaries, filter unsettled
        List<Payment> successful = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .toList();

        List<UUID> paymentIds = successful.stream().map(Payment::getId).toList();
        Map<UUID, Disbursement> disbursementMap = paymentIds.isEmpty()
                ? Map.of()
                : disbursementRepository.findAllByPaymentIdIn(paymentIds).stream()
                        .collect(Collectors.toMap(d -> d.getPayment().getId(), d -> d));

        Map<UUID, List<Payment>> byBooking = successful.stream()
                .collect(Collectors.groupingBy(p -> p.getBooking().getId()));

        return byBooking.entrySet().stream()
                .map(e -> {
                    UUID bookingId = e.getKey();
                    List<Payment> payments = e.getValue();
                    Booking booking = payments.get(0).getBooking();
                    Map<UUID, Disbursement> subset = payments.stream()
                            .filter(p -> disbursementMap.containsKey(p.getId()))
                            .collect(Collectors.toMap(Payment::getId, p -> disbursementMap.get(p.getId())));
                    return BookingFinancialSummary.from(booking, payments, subset);
                })
                .filter(s -> !s.fullySettled())
                .toList();
    }

    public PlatformProfitSummary platformProfit() {
        List<Payment> successful = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .toList();

        long totalPayments       = successful.size();
        double totalCharged      = successful.stream().mapToDouble(p -> safe(p.getAmount())).sum();
        double totalCommission   = successful.stream().mapToDouble(p -> safe(p.getPlatformFee())).sum();
        double totalProcessing   = successful.stream().mapToDouble(p -> safe(p.getTransactionFee())).sum();
        double totalOwnerPromised = successful.stream().mapToDouble(p -> safe(p.getOwnerPayout())).sum();

        double totalRentalCharged   = successful.stream()
                .filter(p -> p.getChargeType() == PaymentChargeType.RENTAL)
                .mapToDouble(p -> safe(p.getAmount())).sum();
        double totalOverstayCharged = successful.stream()
                .filter(p -> p.getChargeType() == PaymentChargeType.OVERSTAY)
                .mapToDouble(p -> safe(p.getAmount())).sum();
        long pendingOverstayCount = paymentRepository.findAll().stream()
                .filter(p -> p.getChargeType() == PaymentChargeType.OVERSTAY
                        && p.getStatus() == PaymentStatus.PENDING)
                .count();

        double totalDisbursed = disbursementRepository
                .findByStatusOrderByCreatedAtDesc(DisbursementStatus.COMPLETED)
                .stream().mapToDouble(d -> safe(d.getAmount())).sum();
        double totalPendingDisb = disbursementRepository
                .findByStatusOrderByCreatedAtDesc(DisbursementStatus.PENDING)
                .stream().mapToDouble(d -> safe(d.getAmount())).sum()
                + disbursementRepository
                .findByStatusOrderByCreatedAtDesc(DisbursementStatus.PROCESSING)
                .stream().mapToDouble(d -> safe(d.getAmount())).sum();

        double outstandingOwnerDebt = totalOwnerPromised - totalDisbursed;

        return new PlatformProfitSummary(
                totalPayments,
                totalCharged,
                totalRentalCharged,
                totalOverstayCharged,
                pendingOverstayCount,
                totalCommission,
                totalProcessing,
                totalOwnerPromised,
                totalDisbursed,
                totalPendingDisb,
                outstandingOwnerDebt,
                totalCommission   // net profit = commission (processing fully covered by renters)
        );
    }

    private static double safe(Double v) { return v == null ? 0.0 : v; }

    public record PlatformProfitSummary(
            long   totalSuccessfulPayments,
            double totalCharged,
            double totalRentalCharged,
            double totalOverstayCharged,
            long   pendingOverstayPayments,
            double totalCommissionEarned,
            double totalProcessingCollected,
            double totalOwnerPayoutsPromised,
            double totalOwnerPayoutsDisbursed,
            double totalPendingDisbursements,
            double outstandingOwnerDebt,
            double platformNetProfit
    ) {}
}
