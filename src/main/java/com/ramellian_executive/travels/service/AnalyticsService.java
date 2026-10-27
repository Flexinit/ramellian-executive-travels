package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.BookingStatus;
import com.ramellian_executive.travels.entity.DisbursementStatus;
import com.ramellian_executive.travels.entity.PaymentStatus;
import com.ramellian_executive.travels.model.analytics.*;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.DisbursementRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final PaymentRepository paymentRepository;
    private final DisbursementRepository disbursementRepository;
    private final BookingRepository bookingRepository;

    public PlatformSummaryResponse summary(LocalDate from, LocalDate to) {
        ZonedDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC);

        List<Object[]> totalsList = paymentRepository.financialTotals(PaymentStatus.SUCCESSFUL, fromDt, toDt);
        Object[] totals = totalsList.isEmpty() ? new Object[7] : totalsList.get(0);
        double gross = num(totals[0]);
        double platformEarnings = num(totals[1]);
        double ownerPayouts = num(totals[2]);
        double txFees = num(totals[3]);
        double deliveryFees = num(totals[4]);
        double driverCharges = num(totals[5]);
        long successfulCount = totals[6] == null ? 0L : ((Number) totals[6]).longValue();

        List<Object[]> paymentStatuses = paymentRepository.countByStatusBetween(fromDt, toDt);
        long pendingPayments = 0, failedPayments = 0;
        for (Object[] row : paymentStatuses) {
            PaymentStatus s = (PaymentStatus) row[0];
            long cnt = ((Number) row[1]).longValue();
            if (s == PaymentStatus.PENDING) pendingPayments = cnt;
            else if (s == PaymentStatus.FAILED) failedPayments = cnt;
        }

        double disbursed = disbursementRepository.sumByStatus(DisbursementStatus.COMPLETED);
        double pendingDisb = disbursementRepository.sumByStatus(DisbursementStatus.PENDING)
                           + disbursementRepository.sumByStatus(DisbursementStatus.PROCESSING);

        List<Object[]> bookingStatuses = bookingRepository.countByStatusBetween(fromDt, toDt);
        Map<BookingStatus, Long> byCounts = new EnumMap<>(BookingStatus.class);
        long totalBookings = 0;
        for (Object[] row : bookingStatuses) {
            long cnt = ((Number) row[1]).longValue();
            byCounts.put((BookingStatus) row[0], cnt);
            totalBookings += cnt;
        }

        // Growth vs prior equal-length period
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        ZonedDateTime priorFrom = fromDt.minusDays(days);
        List<Object[]> priorList = paymentRepository.financialTotals(PaymentStatus.SUCCESSFUL, priorFrom, fromDt);
        Object[] prior = priorList.isEmpty() ? new Object[7] : priorList.get(0);
        double priorGross = num(prior[0]);
        double priorEarnings = num(prior[1]);
        List<Object[]> priorBookingStatuses = bookingRepository.countByStatusBetween(priorFrom, fromDt);
        long priorBookings = priorBookingStatuses.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        Double revenueGrowthPct = priorGross > 0 ? ((gross - priorGross) / priorGross) * 100 : null;
        Double earningsGrowthPct = priorEarnings > 0 ? ((platformEarnings - priorEarnings) / priorEarnings) * 100 : null;
        Double bookingGrowthPct = priorBookings > 0 ? ((totalBookings - priorBookings) / (double) priorBookings) * 100 : null;

        return new PlatformSummaryResponse(
            gross, platformEarnings, ownerPayouts, txFees, deliveryFees, driverCharges,
            successfulCount > 0 ? gross / successfulCount : 0.0,
            successfulCount, pendingPayments, failedPayments,
            disbursed, pendingDisb,
            totalBookings,
            byCounts.getOrDefault(BookingStatus.PENDING, 0L),
            byCounts.getOrDefault(BookingStatus.CONFIRMED, 0L),
            byCounts.getOrDefault(BookingStatus.ACTIVE, 0L),
            byCounts.getOrDefault(BookingStatus.COMPLETED, 0L),
            byCounts.getOrDefault(BookingStatus.CANCELLED, 0L),
            byCounts.getOrDefault(BookingStatus.REJECTED, 0L),
            revenueGrowthPct, earningsGrowthPct, bookingGrowthPct,
            from, to
        );
    }

    public List<DailyEarningResponse> dailyEarnings(LocalDate from, LocalDate to) {
        ZonedDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC);
        List<Object[]> rows = paymentRepository.dailyEarningsBetween(fromDt, toDt);
        List<DailyEarningResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            result.add(new DailyEarningResponse(
                date, num(row[1]), num(row[2]), num(row[3]), ((Number) row[4]).longValue()
            ));
        }
        return result;
    }

    public BookingAnalyticsResponse bookingAnalytics(LocalDate from, LocalDate to) {
        ZonedDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC);
        List<Object[]> rows = bookingRepository.countByStatusBetween(fromDt, toDt);
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        long total = 0, completed = 0, cancelled = 0, confirmed = 0;
        for (Object[] row : rows) {
            String status = row[0].toString();
            long cnt = ((Number) row[1]).longValue();
            countByStatus.put(status, cnt);
            total += cnt;
            if (row[0] == BookingStatus.COMPLETED) completed = cnt;
            if (row[0] == BookingStatus.CANCELLED) cancelled = cnt;
            if (row[0] == BookingStatus.CONFIRMED || row[0] == BookingStatus.ACTIVE
                    || row[0] == BookingStatus.COMPLETED) confirmed += cnt;
        }
        double cancellationRate = total > 0 ? (cancelled / (double) total) * 100 : 0.0;
        double confirmationRate = total > 0 ? (confirmed / (double) total) * 100 : 0.0;
        return new BookingAnalyticsResponse(total, countByStatus, cancellationRate, confirmationRate, completed, cancelled);
    }

    public List<TopVehicleResponse> topVehicles(LocalDate from, LocalDate to, int limit) {
        ZonedDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC);
        List<Object[]> rows = paymentRepository.topVehicles(fromDt, toDt, limit);
        List<TopVehicleResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new TopVehicleResponse(
                str(row[0]), str(row[1]), str(row[2]),
                ((Number) row[3]).longValue(), num(row[4]), num(row[5])
            ));
        }
        return result;
    }

    public List<TopOwnerResponse> topOwners(LocalDate from, LocalDate to, int limit) {
        ZonedDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC);
        List<Object[]> rows = paymentRepository.topOwners(fromDt, toDt, limit);
        List<TopOwnerResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String name = (str(row[1]) + " " + str(row[2])).trim();
            result.add(new TopOwnerResponse(
                str(row[0]), name, str(row[3]),
                ((Number) row[4]).longValue(), ((Number) row[5]).longValue(),
                num(row[6]), num(row[7]), num(row[8])
            ));
        }
        return result;
    }

    private static double num(Object o) {
        if (o == null) return 0.0;
        return ((Number) o).doubleValue();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
    }
}
