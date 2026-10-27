package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.event.PaymentConfirmedEvent;
import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.repository.*;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PesapalPaymentService {
    private static String shortId(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }
    private static final double DEFAULT_PLATFORM_RATE = 0.01;
    private final BookingRepository bookingRepository;
    private final BookingGroupRepository bookingGroupRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final BookingContractRepository contractRepository;
    private final PesapalEventRepository eventRepository;
    private final PesapalClient pesapalClient;
    private final NotificationService appNotificationService;
    private final VerificationNotificationService notificationService;
    private final PlatformSettingsService platformSettingsService;
    private final ReceiptService receiptService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingService bookingService;

    @Autowired
    public PesapalPaymentService(BookingRepository bookingRepository, BookingGroupRepository bookingGroupRepository,
                                 UserRepository userRepository, PaymentRepository paymentRepository,
                                 BookingContractRepository contractRepository,
                                 PesapalEventRepository eventRepository, PesapalClient pesapalClient,
                                 NotificationService appNotificationService,
                                 VerificationNotificationService notificationService,
                                 PlatformSettingsService platformSettingsService,
                                 ReceiptService receiptService,
                                 ApplicationEventPublisher eventPublisher,
                                 BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingGroupRepository = bookingGroupRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.eventRepository = eventRepository;
        this.pesapalClient = pesapalClient;
        this.appNotificationService = appNotificationService;
        this.notificationService = notificationService;
        this.platformSettingsService = platformSettingsService;
        this.receiptService = receiptService;
        this.eventPublisher = eventPublisher;
        this.bookingService = bookingService;
    }

    @Transactional
    public PaymentCheckoutResponse checkout(UUID bookingId, Authentication authentication) {
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
        Payment existing = paymentRepository.findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL).orElse(null);
        if (existing != null && existing.getProviderOrderTrackingId() != null
                && existing.getStatus() == PaymentStatus.PENDING) {
            return new PaymentCheckoutResponse(existing.getId(), "PESAPAL",
                    existing.getProviderOrderTrackingId(), existing.getTransactionReference(),
                    existing.getStatus().name());
        }
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle().getPricePerDay() * days;
        double fee = base * (platformSettingsService.getCommissionRatePct() / 100.0);
        double txFee = base * (platformSettingsService.getPesapalTransactionFeeRatePct() / 100.0);
        double deliveryFee = booking.isDeliveryRequested() ? platformSettingsService.getDeliveryFlatFee() : 0.0;
        double driverCharge = booking.getBookingMode() == BookingMode.CHAUFFEURED
                ? platformSettingsService.getDriverFlatCharge() : 0.0;
        double totalCharge = base + txFee + deliveryFee + driverCharge;
        String merchantReference = "BOOKING-" + shortId(bookingId) + "-" + shortId(UUID.randomUUID());
        PesapalClient.PesapalOrder order = pesapalClient.submitOrder(
                merchantReference, totalCharge, booking.getUser().getEmailAddress(),
                booking.getUser().getPhoneNumber(), booking.getUser().getFirstName(),
                booking.getUser().getLastName(), "Vehicle rental booking " + bookingId,
                bookingId.toString());
        Payment payment = existing == null ? new Payment() : existing;
        payment.setBooking(booking);
        payment.setChargeType(PaymentChargeType.RENTAL);
        payment.setProvider("PESAPAL");
        payment.setProviderMerchantReference(merchantReference);
        payment.setProviderOrderTrackingId(order.trackingId());
        payment.setTransactionReference(order.iframeUrl());
        payment.setAmount(totalCharge);
        payment.setPlatformFee(fee);
        payment.setTransactionFee(txFee);
        payment.setDeliveryFee(deliveryFee);
        payment.setDriverCharge(driverCharge);
        payment.setOwnerPayout(base - fee);
        payment.setPaymentMethod("PESAPAL");
        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);
        return new PaymentCheckoutResponse(saved.getId(), "PESAPAL",
                order.trackingId(), order.iframeUrl(), saved.getStatus().name());
    }

    @Transactional
    public PaymentCheckoutResponse checkoutGroup(UUID groupId, Authentication authentication) {
        BookingGroup group = bookingGroupRepository != null
                ? bookingGroupRepository.findById(groupId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking group not found"))
                : null;
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking group not found");
        }
        if (!group.getRenter().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the renter can pay for this group");
        }
        List<Booking> bookings = bookingRepository.findByBookingGroupId(groupId);
        if (bookings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No bookings found in this group");
        }
        for (Booking booking : bookings) {
            BookingContract contract = contractRepository.findByBookingId(booking.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Review and accept the contract for all bookings before payment"));
            if (contract.getAcceptedAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Review and accept the contract for all bookings before payment");
            }
        }
        String groupPrefix = "GROUP-" + shortId(groupId) + "-";
        Payment existingFirst = paymentRepository.findFirstByProviderMerchantReferenceStartingWith(groupPrefix).orElse(null);
        if (existingFirst != null && existingFirst.getProviderOrderTrackingId() != null
                && existingFirst.getStatus() == PaymentStatus.PENDING) {
            return new PaymentCheckoutResponse(existingFirst.getId(), "PESAPAL",
                    existingFirst.getProviderOrderTrackingId(), existingFirst.getTransactionReference(),
                    existingFirst.getStatus().name());
        }
        double commissionRate = platformSettingsService.getCommissionRatePct() / 100.0;
        double txFeeRate = platformSettingsService.getPesapalTransactionFeeRatePct() / 100.0;
        double flatDeliveryFee = platformSettingsService.getDeliveryFlatFee();
        double flatDriverCharge = platformSettingsService.getDriverFlatCharge();
        double total = 0.0;
        for (Booking booking : bookings) {
            long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
            long days = Math.max(1, (totalHours + 23) / 24);
            double base = booking.getVehicle().getPricePerDay() * days;
            double deliveryFee = booking.isDeliveryRequested() ? flatDeliveryFee : 0.0;
            double driverCharge = booking.getBookingMode() == BookingMode.CHAUFFEURED ? flatDriverCharge : 0.0;
            total += base + base * txFeeRate + deliveryFee + driverCharge;
        }
        User renter = group.getRenter();
        String merchantReference = groupPrefix + shortId(UUID.randomUUID());
        PesapalClient.PesapalOrder order = pesapalClient.submitOrder(
                merchantReference, total, renter.getEmailAddress(),
                renter.getPhoneNumber(), renter.getFirstName(), renter.getLastName(),
                "Batch booking group " + groupId, groupId.toString());
        Payment firstSaved = null;
        for (Booking booking : bookings) {
            long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
            long days = Math.max(1, (totalHours + 23) / 24);
            double base = booking.getVehicle().getPricePerDay() * days;
            double fee = base * commissionRate;
            double txFee = base * txFeeRate;
            double deliveryFee = booking.isDeliveryRequested() ? flatDeliveryFee : 0.0;
            double driverCharge = booking.getBookingMode() == BookingMode.CHAUFFEURED ? flatDriverCharge : 0.0;
            Payment payment = paymentRepository.findByBookingIdAndChargeType(booking.getId(), PaymentChargeType.RENTAL).orElse(new Payment());
            payment.setBooking(booking);
            payment.setProvider("PESAPAL");
            payment.setProviderMerchantReference(merchantReference + "-B" + shortId(booking.getId()));
            payment.setProviderOrderTrackingId(order.trackingId());
            payment.setTransactionReference(order.iframeUrl());
            payment.setAmount(base + txFee + deliveryFee + driverCharge);
            payment.setPlatformFee(fee);
            payment.setTransactionFee(txFee);
            payment.setDeliveryFee(deliveryFee);
            payment.setDriverCharge(driverCharge);
            payment.setOwnerPayout(base - fee);
            payment.setPaymentMethod("PESAPAL");
            payment.setStatus(PaymentStatus.PENDING);
            Payment saved = paymentRepository.save(payment);
            if (firstSaved == null) {
                firstSaved = saved;
            }
        }
        return new PaymentCheckoutResponse(firstSaved.getId(), "PESAPAL",
                order.trackingId(), order.iframeUrl(), firstSaved.getStatus().name());
    }

    @Transactional
    public PaymentCheckoutResponse checkoutOverstay(UUID bookingId, Authentication authentication) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the renter can pay the overstay charge");
        }
        if (booking.getActualEndTime() == null || !booking.getActualEndTime().isAfter(booking.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No overstay recorded for this booking");
        }
        Payment overstayPayment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.OVERSTAY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Overstay charge not found — contact the vehicle owner"));
        if (overstayPayment.getStatus() == PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Overstay charge already paid");
        }
        if (overstayPayment.getStatus() == PaymentStatus.PENDING
                && overstayPayment.getProviderOrderTrackingId() != null) {
            return new PaymentCheckoutResponse(overstayPayment.getId(), "PESAPAL",
                    overstayPayment.getProviderOrderTrackingId(),
                    overstayPayment.getTransactionReference(),
                    overstayPayment.getStatus().name());
        }
        double amount = overstayPayment.getAmount();
        String merchantReference = "OVERSTAY-" + shortId(bookingId) + "-" + shortId(UUID.randomUUID());
        PesapalClient.PesapalOrder order = pesapalClient.submitOrder(
                merchantReference, amount, booking.getUser().getEmailAddress(),
                booking.getUser().getPhoneNumber(), booking.getUser().getFirstName(),
                booking.getUser().getLastName(), "Overstay charge for booking " + bookingId,
                bookingId.toString());
        overstayPayment.setProvider("PESAPAL");
        overstayPayment.setProviderMerchantReference(merchantReference);
        overstayPayment.setProviderOrderTrackingId(order.trackingId());
        overstayPayment.setTransactionReference(order.iframeUrl());
        overstayPayment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(overstayPayment);
        return new PaymentCheckoutResponse(saved.getId(), "PESAPAL",
                order.trackingId(), order.iframeUrl(), saved.getStatus().name());
    }

    @Transactional
    public void processCallback(String trackingId, String merchantReference) {
        // Handle overstay payments
        if (merchantReference.startsWith("OVERSTAY-")) {
            Payment payment = paymentRepository.findByProviderMerchantReference(merchantReference)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Overstay payment not found"));
            if (payment.getStatus() == PaymentStatus.SUCCESSFUL || payment.getStatus() == PaymentStatus.FAILED) {
                return;
            }
            if (eventRepository.existsByOrderTrackingIdAndEventType(trackingId, "PAYMENT_STATUS")) {
                return;
            }
            PesapalClient.PesapalTransaction transaction = pesapalClient.status(trackingId);
            boolean successful = transaction.statusCode() == 1
                    || "COMPLETED".equalsIgnoreCase(transaction.description())
                    || "SUCCESS".equalsIgnoreCase(transaction.description());
            boolean failed = transaction.statusCode() > 1;
            if (successful || failed) {
                PesapalEvent event = new PesapalEvent();
                event.setOrderTrackingId(trackingId);
                event.setMerchantReference(merchantReference);
                event.setEventType("PAYMENT_STATUS");
                event.setStatusCode(transaction.statusCode());
                event.setRawStatus(transaction.description());
                eventRepository.save(event);
            }
            if (successful) {
                payment.setStatus(PaymentStatus.SUCCESSFUL);
                payment.setProviderReceipt(transaction.receipt());
                Payment savedOverstay = paymentRepository.save(payment);
                eventPublisher.publishEvent(new PaymentConfirmedEvent(savedOverstay));
                return;
            } else if (failed) {
                payment.setStatus(PaymentStatus.FAILED);
            }
            paymentRepository.save(payment);
            return;
        }
        // Handle group payments: merchantReference may be "GROUP-{groupId}-{uuid}-B{bookingId}"
        if (merchantReference.startsWith("GROUP-")) {
            List<Payment> groupPayments = paymentRepository.findAllByProviderOrderTrackingId(trackingId);
            if (groupPayments.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
            }
            if (eventRepository.existsByOrderTrackingIdAndEventType(trackingId, "PAYMENT_STATUS")) {
                return;
            }
            PesapalClient.PesapalTransaction transaction = pesapalClient.status(trackingId);
            boolean successful = transaction.statusCode() == 1
                    || "COMPLETED".equalsIgnoreCase(transaction.description())
                    || "SUCCESS".equalsIgnoreCase(transaction.description());
            boolean failed = transaction.statusCode() > 1;
            if (successful || failed) {
                PesapalEvent event = new PesapalEvent();
                event.setOrderTrackingId(trackingId);
                event.setMerchantReference(merchantReference);
                event.setEventType("PAYMENT_STATUS");
                event.setStatusCode(transaction.statusCode());
                event.setRawStatus(transaction.description());
                eventRepository.save(event);
            }
            for (Payment payment : groupPayments) {
                if (payment.getStatus() == PaymentStatus.SUCCESSFUL || payment.getStatus() == PaymentStatus.FAILED) {
                    continue;
                }
                if (successful) {
                    payment.setStatus(PaymentStatus.SUCCESSFUL);
                    payment.setProviderReceipt(transaction.receipt());
                    Booking booking = payment.getBooking();
                    booking.setStatus(BookingStatus.CONFIRMED);
                    bookingRepository.save(booking);
                    bookingService.notifyBookingConfirmed(booking);
                    Payment savedGroup = paymentRepository.save(payment);
                    eventPublisher.publishEvent(new PaymentConfirmedEvent(savedGroup));
                } else if (failed) {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                }
            }
            return;
        }
        Payment payment = paymentRepository.findByProviderMerchantReference(merchantReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (!"PESAPAL".equals(payment.getProvider())
                || !trackingId.equals(payment.getProviderOrderTrackingId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pesapal payment reference does not match the stored payment");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESSFUL
                || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        if (eventRepository.existsByOrderTrackingIdAndEventType(trackingId, "PAYMENT_STATUS")) {
            return;
        }
        PesapalClient.PesapalTransaction transaction = pesapalClient.status(trackingId);
        boolean successful = transaction.statusCode() == 1
                || "COMPLETED".equalsIgnoreCase(transaction.description())
                || "SUCCESS".equalsIgnoreCase(transaction.description());
        boolean failed = transaction.statusCode() > 1;
        if (successful || failed) {
            PesapalEvent event = new PesapalEvent();
            event.setOrderTrackingId(trackingId);
            event.setMerchantReference(merchantReference);
            event.setEventType("PAYMENT_STATUS");
            event.setStatusCode(transaction.statusCode());
            event.setRawStatus(transaction.description());
            eventRepository.save(event);
        }
        if (successful) {
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setProviderReceipt(transaction.receipt());
            Booking booking = payment.getBooking();
            if (booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                bookingService.notifyBookingConfirmed(booking);
            }
            Payment saved = paymentRepository.save(payment);
            Payment withReceipt = receiptService.generate(saved);
            notificationService.sendPaymentReceipt(withReceipt);
            eventPublisher.publishEvent(new PaymentConfirmedEvent(withReceipt));
        } else if (failed) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }

    @Transactional
    public PaymentStatusResponse status(UUID bookingId, Authentication authentication) {
        Payment payment = paymentRepository.findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        boolean renter = payment.getBooking().getUser().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        boolean owner = payment.getBooking().getVehicle().getOwner().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(a -> "PAYMENT_MANAGE".equals(a.getAuthority()));
        if (!renter && !owner && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this payment");
        }
        if ("PESAPAL".equals(payment.getProvider())
                && payment.getProviderOrderTrackingId() != null
                && payment.getProviderMerchantReference() != null
                && payment.getStatus() == PaymentStatus.PENDING) {
            processCallback(payment.getProviderOrderTrackingId(),
                    payment.getProviderMerchantReference());
            payment = paymentRepository.findById(payment.getId()).orElse(payment);
        }
        return PaymentStatusResponse.from(payment);
    }
}
