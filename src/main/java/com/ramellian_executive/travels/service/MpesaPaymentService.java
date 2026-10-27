package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.repository.*;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MpesaPaymentService {

    private static final Logger log = LoggerFactory.getLogger(MpesaPaymentService.class);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingContractRepository contractRepository;
    private final DarajaClient darajaClient;
    private final PlatformSettingsService platformSettingsService;
    private final BookingService bookingService;
    private final NotificationService appNotificationService;
    private final VerificationNotificationService notificationService;
    private final ReceiptService receiptService;

    @Transactional
    public PaymentCheckoutResponse checkout(UUID bookingId, String phone, Authentication authentication) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the renter can pay");
        }
        BookingContract contract = contractRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Review and accept the contract before payment"));
        if (contract.getAcceptedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Review and accept the contract before payment");
        }

        Payment existing = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL).orElse(null);
        if (existing != null && existing.getStatus() == PaymentStatus.PENDING
                && "MPESA".equals(existing.getProvider())
                && existing.getProviderOrderTrackingId() != null) {
            return checkoutResponse(existing, null, null, null, null);
        }

        String resolvedPhone = (phone != null && !phone.isBlank())
                ? phone : booking.getUser().getPhoneNumber();
        if (resolvedPhone == null || resolvedPhone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phone number is required for M-Pesa payment");
        }

        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle().getPricePerDay() * days;
        double platformFee = base * (platformSettingsService.getCommissionRatePct() / 100.0);
        // Processing fees (STK Push + B2C) fully charged to renter; owner bears none
        double processingFee = platformSettingsService.getMpesaStkFee()
                + platformSettingsService.getMpesaB2cFee();
        double deliveryFee = booking.isDeliveryRequested() ? platformSettingsService.getDeliveryFlatFee() : 0.0;
        double driverCharge = booking.getBookingMode() == BookingMode.CHAUFFEURED
                ? platformSettingsService.getDriverFlatCharge() : 0.0;
        double totalCharge = base + processingFee + deliveryFee + driverCharge;
        double ownerPayout = base - platformFee;

        DarajaClient.StkPushResult stk = darajaClient.stkPush(
                resolvedPhone, totalCharge, bookingId.toString(),
                "Vehicle rental booking " + bookingId);

        Payment payment = existing == null ? new Payment() : existing;
        payment.setBooking(booking);
        payment.setChargeType(PaymentChargeType.RENTAL);
        payment.setProvider("MPESA");
        payment.setProviderOrderTrackingId(stk.checkoutRequestId());
        payment.setProviderMerchantReference(stk.merchantRequestId());
        payment.setAmount(totalCharge);
        payment.setPlatformFee(platformFee);
        payment.setTransactionFee(processingFee);
        payment.setDeliveryFee(deliveryFee);
        payment.setDriverCharge(driverCharge);
        payment.setOwnerPayout(ownerPayout);
        payment.setPaymentMethod("MPESA");
        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);

        log.info("STK Push initiated for booking {} — checkoutRequestId={} total={} processingFee={}",
                bookingId, stk.checkoutRequestId(), totalCharge, processingFee);
        return checkoutResponse(saved, base, processingFee, deliveryFee, driverCharge);
    }

    /**
     * Handles the STK Push callback posted by Safaricom.
     * Expected structure:
     * { "Body": { "stkCallback": { "ResultCode": 0, "CheckoutRequestID": "...",
     *   "CallbackMetadata": { "Item": [...] } } } }
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void handleStkCallback(Map<String, Object> payload) {
        try {
            Map<String, Object> body = (Map<String, Object>) payload.get("Body");
            Map<String, Object> callback = (Map<String, Object>) body.get("stkCallback");
            String checkoutRequestId = String.valueOf(callback.get("CheckoutRequestID"));
            int resultCode = ((Number) callback.get("ResultCode")).intValue();

            List<Payment> payments = paymentRepository.findAllByProviderOrderTrackingId(checkoutRequestId);
            if (payments.isEmpty()) {
                log.warn("STK callback received for unknown CheckoutRequestID: {}", checkoutRequestId);
                return;
            }
            Payment payment = payments.get(0);

            if (resultCode != 0) {
                String desc = String.valueOf(callback.getOrDefault("ResultDesc", "Failed"));
                log.warn("STK Push failed for booking {} — ResultCode={} Desc={}",
                        payment.getBooking().getId(), resultCode, desc);
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                return;
            }

            Map<String, Object> meta = (Map<String, Object>) callback.get("CallbackMetadata");
            List<Map<String, Object>> items = (List<Map<String, Object>>) meta.get("Item");
            String receipt = DarajaClient.extractResultParam(items, "MpesaReceiptNumber");
            String amountStr = DarajaClient.extractResultParam(items, "Amount");

            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setProviderReceipt(receipt);
            if (amountStr != null) {
                try { payment.setAmount(Double.parseDouble(amountStr)); } catch (NumberFormatException ignored) {}
            }
            paymentRepository.save(payment);

            log.info("STK Push succeeded for booking {} — receipt={}", payment.getBooking().getId(), receipt);

            try { receiptService.generate(payment); } catch (Exception e) {
                log.warn("Receipt generation failed for payment {}", payment.getId(), e);
            }
            bookingService.notifyBookingConfirmed(payment.getBooking());

        } catch (Exception e) {
            log.error("Error processing STK callback: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getStatus(UUID bookingId, Authentication authentication) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return PaymentStatusResponse.from(payment);
    }

    private PaymentCheckoutResponse checkoutResponse(Payment p, Double base,
                                                      Double txFee, Double deliveryFee, Double driverCharge) {
        return new PaymentCheckoutResponse(
                p.getId(), "MPESA",
                p.getProviderOrderTrackingId(), null,
                p.getStatus().name(),
                base, txFee, deliveryFee, driverCharge,
                p.getPlatformFee(), p.getAmount());
    }
}
