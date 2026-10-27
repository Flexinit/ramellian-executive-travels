package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.RefundRequest;
import com.ramellian_executive.travels.model.RefundResponse;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PesapalClient pesapalClient;

    @Transactional
    public RefundResponse requestRefund(UUID bookingId, RefundRequest request, Authentication authentication) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No rental payment found for this booking"));

        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment must be SUCCESSFUL before a refund can be issued");
        }
        if (payment.getProviderOrderTrackingId() == null || payment.getProviderOrderTrackingId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Payment has no Pesapal order tracking ID — cannot issue Pesapal refund");
        }
        if (refundRepository.existsByPaymentId(payment.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A refund already exists for this payment");
        }

        double refundAmount = (request != null && request.amount() != null && request.amount() > 0)
                ? request.amount()
                : payment.getAmount();
        String reason = request != null ? request.reason() : null;

        PesapalClient.PesapalRefundResult result =
                pesapalClient.refund(payment.getProviderOrderTrackingId(), refundAmount, reason);

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setAmount(refundAmount);
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setPesapalRefundTrackingId(result.refundTrackingId());
        refund.setInitiatedBy(authentication.getName());

        if (refundAmount >= payment.getAmount()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
        }

        log.info("Refund initiated for booking {} — amount={} trackingId={}",
                bookingId, refundAmount, result.refundTrackingId());

        return RefundResponse.from(refundRepository.save(refund));
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> list(RefundStatus status) {
        List<Refund> results = status != null
                ? refundRepository.findByStatusOrderByCreatedAtDesc(status)
                : refundRepository.findAllByOrderByCreatedAtDesc();
        return results.stream().map(RefundResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse getByBooking(UUID bookingId) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for booking"));
        Refund refund = refundRepository.findByPaymentId(payment.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No refund found for this booking"));
        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    public RefundResponse getById(UUID id) {
        return RefundResponse.from(
                refundRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found")));
    }

    @Transactional
    public RefundResponse markCompleted(UUID id, Authentication authentication) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund is already completed");
        }
        refund.setStatus(RefundStatus.COMPLETED);
        Payment payment = refund.getPayment();
        if (payment != null && refund.getAmount() >= payment.getAmount()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
        }
        return RefundResponse.from(refundRepository.save(refund));
    }

    @Transactional
    public RefundResponse markFailed(UUID id, Authentication authentication) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
        if (refund.getStatus() == RefundStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot fail a completed refund");
        }
        refund.setStatus(RefundStatus.FAILED);
        return RefundResponse.from(refundRepository.save(refund));
    }
}
