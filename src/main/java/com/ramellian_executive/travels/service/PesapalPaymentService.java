package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class PesapalPaymentService {
    private static final double PLATFORM_RATE = 0.01;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingContractRepository contractRepository;
    private final PesapalEventRepository eventRepository;
    private final PesapalClient pesapalClient;

    public PesapalPaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
                                 BookingContractRepository contractRepository,
                                 PesapalEventRepository eventRepository, PesapalClient pesapalClient) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.eventRepository = eventRepository;
        this.pesapalClient = pesapalClient;
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
        Payment existing = paymentRepository.findByBookingId(bookingId).orElse(null);
        if (existing != null && existing.getProviderOrderTrackingId() != null
                && existing.getStatus() == PaymentStatus.PENDING) {
            return new PaymentCheckoutResponse(existing.getId(), "PESAPAL",
                    existing.getProviderOrderTrackingId(), existing.getTransactionReference(),
                    existing.getStatus().name());
        }
        long days = Math.max(1, java.time.Duration.between(
                booking.getStartTime(), booking.getEndTime()).toDays());
        double amount = booking.getVehicle().getPricePerDay() * days;
        String merchantReference = "BOOKING-" + bookingId + "-" + UUID.randomUUID();
        PesapalClient.PesapalOrder order = pesapalClient.submitOrder(
                merchantReference, amount, booking.getUser().getEmailAddress(),
                booking.getUser().getPhoneNumber(), booking.getUser().getFirstName(),
                booking.getUser().getLastName(), "Vehicle rental booking " + bookingId);
        Payment payment = existing == null ? new Payment() : existing;
        payment.setBooking(booking);
        payment.setProvider("PESAPAL");
        payment.setProviderMerchantReference(merchantReference);
        payment.setProviderOrderTrackingId(order.trackingId());
        payment.setTransactionReference(order.redirectUrl());
        payment.setAmount(amount);
        payment.setPlatformFee(amount * PLATFORM_RATE);
        payment.setOwnerPayout(amount - amount * PLATFORM_RATE);
        payment.setPaymentMethod("PESAPAL");
        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);
        return new PaymentCheckoutResponse(saved.getId(), "PESAPAL",
                order.trackingId(), order.redirectUrl(), saved.getStatus().name());
    }

    @Transactional
    public void processCallback(String trackingId, String merchantReference) {
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
            payment.getBooking().setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(payment.getBooking());
        } else if (failed) {
            payment.setStatus(PaymentStatus.FAILED);
        }
        paymentRepository.save(payment);
    }

    @Transactional
    public PaymentStatusResponse status(UUID bookingId, Authentication authentication) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
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
