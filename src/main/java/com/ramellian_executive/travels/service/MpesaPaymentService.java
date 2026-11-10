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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final PaymentTransactionService paymentTransactionService;
    private final ObjectMapper objectMapper;

    /**
     * Every 2 minutes: query Safaricom for all PENDING M-Pesa payments that have a checkoutRequestId.
     * This ensures payments are confirmed even when the callback is delayed or never arrives.
     */
    @Scheduled(fixedDelay = 2 * 60 * 1000)
    @Transactional
    public void pollPendingMpesaPayments() {
        List<Payment> pending = paymentRepository
                .findAllByStatusAndProviderAndProviderOrderTrackingIdIsNotNull(
                        PaymentStatus.PENDING, "MPESA");
        if (pending.isEmpty()) return;
        log.info("Polling {} PENDING M-Pesa payment(s) with Safaricom", pending.size());
        for (Payment payment : pending) {
            try {
                DarajaClient.StkQueryResult query = darajaClient.queryStkStatus(
                        payment.getProviderOrderTrackingId());
                int resultCode = Integer.parseInt(query.resultCode());
                if (resultCode == 0) {
                    payment.setStatus(PaymentStatus.SUCCESSFUL);
                    paymentRepository.save(payment);
                    Booking booking = payment.getBooking();
                    User renter = booking.getUser();
                    if (booking.getStatus() == BookingStatus.PENDING) {
                        booking.setStatus(BookingStatus.CONFIRMED);
                        bookingRepository.save(booking);
                    }
                    paymentTransactionService.record(
                            PaymentTransactionType.MPESA_STK, "SUCCESSFUL",
                            payment.getAmount(),
                            renter.getPhoneNumber(),
                            trim(renter.getFirstName()) + " " + trim(renter.getLastName()),
                            renter.getEmailAddress(),
                            "STK poll-confirmed for booking " + booking.getId(),
                            booking.getId(), payment.getId(),
                            null, null,
                            null, payment.getProviderMerchantReference(), query.resultDesc());
                    try { receiptService.generate(payment); } catch (Exception e) {
                        log.warn("Receipt generation failed for payment {}", payment.getId(), e);
                    }
                    bookingService.notifyBookingConfirmed(booking);
                    log.info("Poller: payment {} CONFIRMED for booking {}", payment.getId(), booking.getId());
                } else if (resultCode != 1) {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    User renter = payment.getBooking().getUser();
                    paymentTransactionService.record(
                            PaymentTransactionType.MPESA_STK, "FAILED",
                            payment.getAmount(),
                            renter.getPhoneNumber(),
                            trim(renter.getFirstName()) + " " + trim(renter.getLastName()),
                            renter.getEmailAddress(),
                            "STK poll-failed for booking " + payment.getBooking().getId(),
                            payment.getBooking().getId(), payment.getId(),
                            null, null,
                            null, payment.getProviderMerchantReference(), query.resultDesc());
                    log.warn("Poller: payment {} FAILED for booking {} — ResultCode={} Desc={}",
                            payment.getId(), payment.getBooking().getId(), resultCode, query.resultDesc());
                }
            } catch (Exception e) {
                log.debug("Poller: STK query skipped for payment {}: {}", payment.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public PaymentCheckoutResponse checkout(UUID bookingId, String phone, Authentication authentication) {
        Booking booking = requireBookingOwnedBy(bookingId, authentication);
        requireAcceptedContract(bookingId);

        Payment existing = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL).orElse(null);
        if (existing != null && existing.getStatus() == PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking rental is already paid — receipt: " + existing.getProviderReceipt());
        }
        if (existing != null && existing.getStatus() == PaymentStatus.PENDING
                && "MPESA".equals(existing.getProvider())
                && existing.getProviderOrderTrackingId() != null) {
            return checkoutResponse(existing, null, null, null, null);
        }

        String resolvedPhone = resolvePhone(phone, booking);
        BookingFees fees = calculateFees(booking);
        DarajaClient.StkPushResult stk = darajaClient.stkPush(
                resolvedPhone, fees.total(), "Ramellian",
                "Vehicle Rental");

        Payment payment = applyCheckout(existing == null ? new Payment() : existing,
                booking, stk, fees);
        log.info("STK Push initiated for booking {} — checkoutRequestId={} total={}",
                bookingId, stk.checkoutRequestId(), fees.total());
        return checkoutResponse(payment, fees.base(), fees.processing(), fees.delivery(), fees.driver());
    }

    /** Initiates M-Pesa STK Push for a pending overstay charge on this booking. */
    @Transactional
    public PaymentCheckoutResponse checkoutOverstay(UUID bookingId, String phone, Authentication authentication) {
        Booking booking = requireBookingOwnedBy(bookingId, authentication);

        Payment overstay = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.OVERSTAY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No overstay charge found for this booking"));

        if (overstay.getStatus() == PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Overstay charge is already paid — receipt: " + overstay.getProviderReceipt());
        }
        if (overstay.getStatus() == PaymentStatus.PENDING
                && "MPESA".equals(overstay.getProvider())
                && overstay.getProviderOrderTrackingId() != null) {
            return checkoutResponse(overstay, null, null, null, null);
        }

        String resolvedPhone = resolvePhone(phone, booking);
        DarajaClient.StkPushResult stk = darajaClient.stkPush(
                resolvedPhone, overstay.getAmount(), "Ramellian",
                "Overstay Charge");

        overstay.setProvider("MPESA");
        overstay.setPaymentMethod("MPESA");
        overstay.setProviderOrderTrackingId(stk.checkoutRequestId());
        overstay.setProviderMerchantReference(stk.merchantRequestId());
        overstay.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(overstay);

        log.info("Overstay STK Push initiated for booking {} — checkoutRequestId={} amount={}",
                bookingId, stk.checkoutRequestId(), overstay.getAmount());
        return checkoutResponse(saved, null, null, null, null);
    }

    @Transactional
    public PaymentStatusResponse getOverstayStatus(UUID bookingId, Authentication authentication) {
        requireBookingOwnedBy(bookingId, authentication);

        Payment overstay = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.OVERSTAY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No overstay charge found for this booking"));

        if (overstay.getStatus() == PaymentStatus.PENDING
                && "MPESA".equals(overstay.getProvider())
                && overstay.getProviderOrderTrackingId() != null) {
            try {
                DarajaClient.StkQueryResult query = darajaClient.queryStkStatus(
                        overstay.getProviderOrderTrackingId());
                int resultCode = Integer.parseInt(query.resultCode());
                if (resultCode == 0) {
                    overstay.setStatus(PaymentStatus.SUCCESSFUL);
                    paymentRepository.save(overstay);
                    log.info("Overstay STK poll resolved PENDING→SUCCESSFUL for booking {}", bookingId);
                } else if (resultCode != 1) {
                    overstay.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(overstay);
                }
            } catch (Exception e) {
                log.debug("Overstay STK query skipped for booking {}: {}", bookingId, e.getMessage());
            }
        }
        return PaymentStatusResponse.from(overstay);
    }

    /** Re-sends the STK prompt for any PENDING or FAILED M-Pesa payment on this booking. */
    @Transactional
    public PaymentCheckoutResponse retryStkPush(UUID bookingId, String phone, Authentication authentication) {
        Booking booking = requireBookingOwnedBy(bookingId, authentication);

        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No M-Pesa payment found for this booking — use checkout first"));

        if (payment.getStatus() == PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is already successful");
        }
        if (!"MPESA".equals(payment.getProvider())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment provider is " + payment.getProvider() + ", not M-Pesa");
        }

        String resolvedPhone = resolvePhone(phone, booking);
        BookingFees fees = new BookingFees(
                payment.getAmount() - (payment.getTransactionFee() != null ? payment.getTransactionFee() : 0)
                        - (payment.getDeliveryFee() != null ? payment.getDeliveryFee() : 0)
                        - (payment.getDriverCharge() != null ? payment.getDriverCharge() : 0),
                payment.getTransactionFee() != null ? payment.getTransactionFee() : 0,
                payment.getDeliveryFee() != null ? payment.getDeliveryFee() : 0,
                payment.getDriverCharge() != null ? payment.getDriverCharge() : 0,
                payment.getAmount(),
                payment.getPlatformFee() != null ? payment.getPlatformFee() : 0,
                payment.getOwnerPayout() != null ? payment.getOwnerPayout() : 0);

        DarajaClient.StkPushResult stk = darajaClient.stkPush(
                resolvedPhone, payment.getAmount(), "Ramellian",
                "Vehicle Rental");

        payment.setProviderOrderTrackingId(stk.checkoutRequestId());
        payment.setProviderMerchantReference(stk.merchantRequestId());
        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);

        log.info("STK Push retried for booking {} — checkoutRequestId={}", bookingId, stk.checkoutRequestId());
        return checkoutResponse(saved, fees.base(), fees.processing(), fees.delivery(), fees.driver());
    }

    private Booking requireBookingOwnedBy(UUID bookingId, Authentication auth) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the renter can pay");
        }
        return booking;
    }

    private void requireAcceptedContract(UUID bookingId) {
        BookingContract contract = contractRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Review and accept the contract before payment"));
        if (contract.getAcceptedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Review and accept the contract before payment");
        }
    }

    private String resolvePhone(String phone, Booking booking) {
        String resolved = (phone != null && !phone.isBlank()) ? phone : booking.getUser().getPhoneNumber();
        if (resolved == null || resolved.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Phone number is required for M-Pesa payment");
        }
        return resolved;
    }

    private BookingFees calculateFees(Booking booking) {
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle().getPricePerDay() * days;
        double platformFee = base * (platformSettingsService.getCommissionRatePct(days) / 100.0);
        double processing = platformSettingsService.getMpesaStkFee() + platformSettingsService.getMpesaB2cFee();
        double delivery = booking.isDeliveryRequested() ? platformSettingsService.getDeliveryFlatFee() : 0.0;
        double driver = booking.getBookingMode() == BookingMode.CHAUFFEURED
                ? platformSettingsService.getDriverFlatCharge() : 0.0;
        return new BookingFees(base, processing, delivery, driver, base + processing + delivery + driver,
                platformFee, base - platformFee);
    }

    private Payment applyCheckout(Payment payment, Booking booking,
                                   DarajaClient.StkPushResult stk, BookingFees fees) {
        payment.setBooking(booking);
        payment.setChargeType(PaymentChargeType.RENTAL);
        payment.setProvider("MPESA");
        payment.setProviderOrderTrackingId(stk.checkoutRequestId());
        payment.setProviderMerchantReference(stk.merchantRequestId());
        payment.setAmount(fees.total());
        payment.setPlatformFee(fees.platformFee());
        payment.setTransactionFee(fees.processing());
        payment.setDeliveryFee(fees.delivery());
        payment.setDriverCharge(fees.driver());
        payment.setOwnerPayout(fees.ownerPayout());
        payment.setPaymentMethod("MPESA");
        payment.setStatus(PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    private record BookingFees(double base, double processing, double delivery, double driver,
                                double total, double platformFee, double ownerPayout) {}

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
            String rawJson = safeJson(payload);
            Map<String, Object> body = (Map<String, Object>) payload.get("Body");
            Map<String, Object> callback = (Map<String, Object>) body.get("stkCallback");

            String checkoutRequestId = String.valueOf(callback.get("CheckoutRequestID"));
            String merchantRequestId = String.valueOf(callback.getOrDefault("MerchantRequestID", ""));
            int resultCode = ((Number) callback.get("ResultCode")).intValue();
            String resultDesc = String.valueOf(callback.getOrDefault("ResultDesc", ""));

            List<Payment> payments = paymentRepository.findAllByProviderOrderTrackingId(checkoutRequestId);
            if (payments.isEmpty()) {
                log.warn("STK callback received for unknown CheckoutRequestID: {}", checkoutRequestId);
                return;
            }
            Payment payment = payments.get(0);
            if (payment.getStatus() == PaymentStatus.SUCCESSFUL) {
                log.info("STK callback for already-paid payment {} — ignoring duplicate", payment.getId());
                return;
            }

            Booking booking = payment.getBooking();
            User renter = booking.getUser();

            if (resultCode != 0) {
                log.warn("STK Push failed for booking {} — ResultCode={} Desc={}",
                        booking.getId(), resultCode, resultDesc);
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                PaymentTransaction tx = paymentTransactionService.record(
                        PaymentTransactionType.MPESA_STK, "FAILED",
                        payment.getAmount(),
                        renter.getPhoneNumber(),
                        trim(renter.getFirstName()) + " " + trim(renter.getLastName()),
                        renter.getEmailAddress(),
                        "STK Push for booking " + booking.getId(),
                        booking.getId(), payment.getId(),
                        null, null,
                        null, merchantRequestId, resultDesc, rawJson);
                if (tx != null) {
                    paymentTransactionService.updateMpesaFields(tx.getId(),
                            merchantRequestId, checkoutRequestId,
                            resultCode, resultDesc,
                            null, null, null, null);
                }
                return;
            }

            Map<String, Object> meta = (Map<String, Object>) callback.get("CallbackMetadata");
            List<Map<String, Object>> items = (List<Map<String, Object>>) meta.get("Item");

            String receipt     = DarajaClient.extractStkItem(items, "MpesaReceiptNumber");
            String amountStr   = DarajaClient.extractStkItem(items, "Amount");
            String txDate      = DarajaClient.extractStkItem(items, "TransactionDate");
            String mpesaPhone  = DarajaClient.extractStkItem(items, "PhoneNumber");
            String balance     = DarajaClient.extractStkItem(items, "Balance");

            double paidAmount = payment.getAmount();
            if (amountStr != null) {
                try { paidAmount = Double.parseDouble(amountStr); } catch (NumberFormatException ignored) {}
            }
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setProviderReceipt(receipt);
            payment.setAmount(paidAmount);
            paymentRepository.save(payment);

            if (booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
            }

            log.info("STK Push succeeded for booking {} — receipt={} phone={} amount={}",
                    booking.getId(), receipt, mpesaPhone, paidAmount);

            String renterPhone = renter.getPhoneNumber();
            String renterName  = trim(renter.getFirstName()) + " " + trim(renter.getLastName());
            String actualPhone = mpesaPhone != null ? mpesaPhone : renterPhone;
            boolean paidByDifferentPhone = mpesaPhone != null
                    && !mpesaPhone.equals(renterPhone)
                    && !mpesaPhone.equals(DarajaClient.normalizePhone(renterPhone != null ? renterPhone : ""));
            // renterPhone/renterName only stored when payer ≠ renter
            PaymentTransaction tx = paymentTransactionService.record(
                    PaymentTransactionType.MPESA_STK, "SUCCESSFUL",
                    paidAmount,
                    actualPhone,
                    renterName,
                    renter.getEmailAddress(),
                    "STK Push for booking " + booking.getId(),
                    booking.getId(), payment.getId(),
                    null, null,
                    receipt, merchantRequestId, resultDesc, rawJson,
                    paidByDifferentPhone ? renterPhone : null,
                    paidByDifferentPhone ? renterName  : null);
            if (tx != null) {
                paymentTransactionService.updateMpesaFields(tx.getId(),
                        merchantRequestId, checkoutRequestId,
                        resultCode, resultDesc,
                        receipt, txDate, mpesaPhone, balance);
            }

            try { receiptService.generate(payment); } catch (Exception e) {
                log.warn("Receipt generation failed for payment {}", payment.getId(), e);
            }
            bookingService.notifyBookingConfirmed(booking);

        } catch (Exception e) {
            log.error("Error processing STK callback: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public PaymentStatusResponse getStatus(UUID bookingId, Authentication authentication) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (payment.getStatus() == PaymentStatus.PENDING
                && "MPESA".equals(payment.getProvider())
                && payment.getProviderOrderTrackingId() != null) {
            try {
                DarajaClient.StkQueryResult query = darajaClient.queryStkStatus(
                        payment.getProviderOrderTrackingId());
                int resultCode = Integer.parseInt(query.resultCode());
                if (resultCode == 0) {
                    payment.setStatus(PaymentStatus.SUCCESSFUL);
                    paymentRepository.save(payment);
                    Booking booking = payment.getBooking();
                    if (booking.getStatus() == BookingStatus.PENDING) {
                        booking.setStatus(BookingStatus.CONFIRMED);
                        bookingRepository.save(booking);
                    }
                    try { receiptService.generate(payment); } catch (Exception e) {
                        log.warn("Receipt generation failed for payment {}", payment.getId(), e);
                    }
                    bookingService.notifyBookingConfirmed(booking);
                    log.info("STK status poll resolved PENDING→SUCCESSFUL for booking {}", bookingId);
                } else if (resultCode != 1 /* still processing = code 1 on sandbox */) {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    log.info("STK status poll resolved PENDING→FAILED for booking {} resultCode={} desc={}",
                            bookingId, resultCode, query.resultDesc());
                }
            } catch (Exception e) {
                log.debug("STK status query skipped for booking {}: {}", bookingId, e.getMessage());
            }
        }

        return PaymentStatusResponse.from(payment);
    }

    @Transactional
    public PaymentStatusResponse adminSyncPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (!"MPESA".equals(payment.getProvider()) || payment.getProviderOrderTrackingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment is not an M-Pesa STK payment");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESSFUL) {
            return PaymentStatusResponse.from(payment);
        }

        DarajaClient.StkQueryResult query = darajaClient.queryStkStatus(payment.getProviderOrderTrackingId());
        int resultCode = Integer.parseInt(query.resultCode());

        if (resultCode == 0) {
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            paymentRepository.save(payment);
            Booking booking = payment.getBooking();
            if (booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
            }
            try { receiptService.generate(payment); } catch (Exception e) {
                log.warn("Receipt generation failed for payment {}", payment.getId(), e);
            }
            bookingService.notifyBookingConfirmed(booking);
            log.info("Admin sync resolved payment {} → SUCCESSFUL", paymentId);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.info("Admin sync resolved payment {} → FAILED resultCode={} desc={}", paymentId, resultCode, query.resultDesc());
        }
        return PaymentStatusResponse.from(payment);
    }

    @Transactional
    public PaymentStatusResponse adminOverridePayment(UUID paymentId, String status, String receipt) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        PaymentStatus newStatus;
        try {
            newStatus = PaymentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Use SUCCESSFUL, FAILED, or PENDING");
        }

        payment.setStatus(newStatus);
        if (receipt != null && !receipt.isBlank()) {
            payment.setProviderReceipt(receipt);
        }
        paymentRepository.save(payment);

        if (newStatus == PaymentStatus.SUCCESSFUL) {
            Booking booking = payment.getBooking();
            if (booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
            }
            try { receiptService.generate(payment); } catch (Exception e) {
                log.warn("Receipt generation failed for payment {}", payment.getId(), e);
            }
            bookingService.notifyBookingConfirmed(booking);
        }
        log.info("Admin manually overrode payment {} → {} receipt={}", paymentId, newStatus, receipt);
        return PaymentStatusResponse.from(payment);
    }

    @Transactional
    public void handleC2bConfirmation(Map<String, Object> payload) {
        try {
            String rawJson   = safeJson(payload);
            String transId   = String.valueOf(payload.getOrDefault("TransID", ""));
            String msisdn    = String.valueOf(payload.getOrDefault("MSISDN", ""));
            String billRef   = String.valueOf(payload.getOrDefault("BillRefNumber", ""));
            double amount    = parseDouble(payload.getOrDefault("TransAmount", "0"));
            String partyName = buildPartyName(payload);

            log.info("C2B confirmation — transId={} billRef={} msisdn={} amount={}", transId, billRef, msisdn, amount);

            UUID bookingId = parseUuid(billRef);
            if (bookingId != null) {
                Booking booking = bookingRepository.findById(bookingId).orElse(null);
                if (booking != null) {
                    confirmC2bPayment(booking, transId, msisdn, partyName, amount, rawJson);
                    return;
                }
            }

            paymentTransactionService.record(PaymentTransactionType.MPESA_C2B, "UNLINKED",
                    amount, msisdn, partyName, null,
                    "C2B — no booking matched BillRefNumber: " + billRef,
                    null, null, null, null, transId, null, null, rawJson);
            log.warn("C2B payment unlinked — billRef={} transId={}", billRef, transId);
        } catch (Exception e) {
            log.error("Error processing C2B confirmation: {}", e.getMessage(), e);
        }
    }

    private void confirmC2bPayment(Booking booking, String transId, String msisdn,
                                    String partyName, double amount, String rawJson) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(booking.getId(), PaymentChargeType.RENTAL)
                .orElseGet(() -> buildC2bPayment(booking));
        if (payment.getStatus() == PaymentStatus.SUCCESSFUL) {
            log.warn("C2B confirmation for already-paid booking {} transId={} — ignoring", booking.getId(), transId);
            return;
        }
        payment.setAmount(amount);
        payment.setProviderReceipt(transId);
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        Payment saved = paymentRepository.save(payment);

        if (booking.getStatus() == BookingStatus.PENDING) {
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
        }

        paymentTransactionService.record(PaymentTransactionType.MPESA_C2B, "SUCCESSFUL",
                amount, msisdn, partyName, null,
                "C2B Paybill for booking " + booking.getId(),
                booking.getId(), saved.getId(), null, null, transId, null, null, rawJson);

        try { receiptService.generate(saved); } catch (Exception e) {
            log.warn("Receipt generation failed for C2B payment on booking {}", booking.getId(), e);
        }
        bookingService.notifyBookingConfirmed(booking);
        log.info("C2B confirmed booking {} — transId={}", booking.getId(), transId);
    }

    private Payment buildC2bPayment(Booking booking) {
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle().getPricePerDay() * days;
        double platformFee = base * (platformSettingsService.getCommissionRatePct(days) / 100.0);

        Payment p = new Payment();
        p.setBooking(booking);
        p.setChargeType(PaymentChargeType.RENTAL);
        p.setPaymentMethod("MPESA");
        p.setProvider("MPESA");
        p.setPlatformFee(platformFee);
        p.setOwnerPayout(base - platformFee);
        p.setTransactionFee(0.0);
        p.setDeliveryFee(booking.isDeliveryRequested() ? platformSettingsService.getDeliveryFlatFee() : 0.0);
        p.setDriverCharge(booking.getBookingMode() == BookingMode.CHAUFFEURED
                ? platformSettingsService.getDriverFlatCharge() : 0.0);
        return p;
    }

    private static String buildPartyName(Map<String, Object> payload) {
        return (trim(String.valueOf(payload.getOrDefault("FirstName", "")))
                + " " + trim(String.valueOf(payload.getOrDefault("MiddleName", "")))
                + " " + trim(String.valueOf(payload.getOrDefault("LastName", "")))).trim();
    }

    private static double parseDouble(Object raw) {
        try { return Double.parseDouble(String.valueOf(raw)); } catch (NumberFormatException e) { return 0; }
    }

    private static UUID parseUuid(String s) {
        try { return UUID.fromString(s.trim()); } catch (Exception e) { return null; }
    }

    private static String trim(String s) { return s != null ? s.trim() : ""; }

    private String safeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return null; }
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
