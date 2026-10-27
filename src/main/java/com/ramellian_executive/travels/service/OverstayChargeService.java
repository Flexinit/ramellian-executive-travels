package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.repository.DisbursementRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class OverstayChargeService {

    private static final Logger log = LoggerFactory.getLogger(OverstayChargeService.class);

    private final PaymentRepository paymentRepository;
    private final DisbursementRepository disbursementRepository;
    private final PlatformSettingsService platformSettingsService;
    private final NotificationService notificationService;

    /**
     * Called at dropoff. If the vehicle is returned late beyond the grace period,
     * creates a PENDING overstay Payment and a PENDING Disbursement for the owner's share.
     * Returns the overstay payment if one was created, null otherwise.
     */
    @Transactional
    public Payment createIfOverstay(Booking booking) {
        ZonedDateTime returnedAt = booking.getActualEndTime() != null
                ? booking.getActualEndTime() : ZonedDateTime.now();
        ZonedDateTime agreedEnd  = booking.getEndTime();

        if (!returnedAt.isAfter(agreedEnd)) return null;

        long overstayHours = Duration.between(agreedEnd, returnedAt).toHours();
        long gracePeriod   = (long) platformSettingsService.getOverstayGracePeriodHours();

        if (overstayHours <= gracePeriod) {
            log.info("Booking {} overstay of {}h is within grace period of {}h — no charge",
                    booking.getId(), overstayHours, gracePeriod);
            return null;
        }

        // Already charged?
        if (paymentRepository.findByBookingIdAndChargeType(booking.getId(), PaymentChargeType.OVERSTAY)
                .isPresent()) {
            log.info("Overstay charge already exists for booking {}", booking.getId());
            return null;
        }

        // Round overstay up to full days beyond grace period
        long billableHours = overstayHours - gracePeriod;
        long overstayDays  = Math.max(1, (billableHours + 23) / 24);

        double dailyRate   = booking.getVehicle().getPricePerDay();
        double multiplier  = platformSettingsService.getOverstayRateMultiplier();
        double baseCharge  = dailyRate * overstayDays * multiplier;

        double commissionRate = platformSettingsService.getCommissionRatePct(overstayDays) / 100.0;
        double processingFee  = platformSettingsService.getMpesaStkFee()
                              + platformSettingsService.getMpesaB2cFee();
        double commission     = baseCharge * commissionRate;
        double totalCharge    = baseCharge + processingFee;
        double ownerPayout    = baseCharge - commission;

        Payment overstay = new Payment();
        overstay.setBooking(booking);
        overstay.setChargeType(PaymentChargeType.OVERSTAY);
        overstay.setProvider("MPESA");
        overstay.setPaymentMethod("MPESA");
        overstay.setAmount(totalCharge);
        overstay.setPlatformFee(commission);
        overstay.setTransactionFee(processingFee);
        overstay.setDeliveryFee(0.0);
        overstay.setDriverCharge(0.0);
        overstay.setOwnerPayout(ownerPayout);
        overstay.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(overstay);

        // Create a PENDING disbursement so the owner's owed amount is tracked immediately
        Disbursement disbursement = new Disbursement();
        disbursement.setPayment(saved);
        disbursement.setAmount(ownerPayout);
        disbursement.setStatus(DisbursementStatus.PENDING);
        User owner = booking.getVehicle().getOwner();
        if (owner != null) {
            disbursement.setOwnerEmail(owner.getEmailAddress());
            disbursement.setOwnerName(trim(owner.getFirstName()) + " " + trim(owner.getLastName()));
            disbursement.setOwnerPhone(owner.getPhoneNumber());
        }
        disbursement.setNotes("Auto-created for overstay of " + overstayHours + "h ("
                + overstayDays + " day(s) charged at " + multiplier + "x rate)");
        disbursementRepository.save(disbursement);

        log.info("Overstay charge created for booking {} — {}h overstay, {} day(s), " +
                "baseCharge={} totalCharge={} ownerPayout={}",
                booking.getId(), overstayHours, overstayDays, baseCharge, totalCharge, ownerPayout);

        // Notify renter
        try {
            notificationService.createNotification(
                    booking.getUser(),
                    "Overstay Charge Applied",
                    String.format("Your vehicle was returned %dh late. An additional charge of KES %.2f " +
                            "has been applied for %d extra day(s). Please settle this outstanding amount.",
                            overstayHours, totalCharge, overstayDays),
                    "OVERSTAY_CHARGE",
                    saved.getId(),
                    "PAYMENT"
            );
        } catch (Exception e) {
            log.warn("Failed to send overstay notification for booking {}", booking.getId(), e);
        }

        return saved;
    }

    public record OverstaySummary(long overstayHours, long overstayDays, double dailyRate,
                                  double multiplier, double baseCharge, double totalCharge) {}

    /** Preview overstay charge without persisting — useful for admin/dropoff UI. */
    public OverstaySummary preview(Booking booking, ZonedDateTime returnAt) {
        ZonedDateTime agreedEnd = booking.getEndTime();
        if (!returnAt.isAfter(agreedEnd)) return null;

        long overstayHours = Duration.between(agreedEnd, returnAt).toHours();
        long gracePeriod   = (long) platformSettingsService.getOverstayGracePeriodHours();
        if (overstayHours <= gracePeriod) return null;

        long billableHours = overstayHours - gracePeriod;
        long overstayDays  = Math.max(1, (billableHours + 23) / 24);
        double dailyRate   = booking.getVehicle().getPricePerDay();
        double multiplier  = platformSettingsService.getOverstayRateMultiplier();
        double baseCharge  = dailyRate * overstayDays * multiplier;
        double processingFee = platformSettingsService.getMpesaStkFee()
                             + platformSettingsService.getMpesaB2cFee();

        return new OverstaySummary(overstayHours, overstayDays, dailyRate,
                multiplier, baseCharge, baseCharge + processingFee);
    }

    private static String trim(String s) { return s != null ? s.trim() : ""; }
}
