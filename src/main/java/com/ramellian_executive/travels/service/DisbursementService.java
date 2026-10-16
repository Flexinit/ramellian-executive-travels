package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.DisbursementResponse;
import com.ramellian_executive.travels.model.DisbursementTriggerRequest;
import com.ramellian_executive.travels.repository.DisbursementRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisbursementService {

    private final DisbursementRepository disbursementRepository;
    private final PaymentRepository paymentRepository;
    private final VerificationNotificationService notificationService;
    private final NotificationService appNotificationService;

    @Transactional
    public void createForPickup(UUID bookingId) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCESSFUL) return;
        if (disbursementRepository.existsByPaymentId(payment.getId())) return;

        Booking booking = payment.getBooking();
        User owner = booking.getVehicle().getOwner();

        Disbursement disbursement = new Disbursement();
        disbursement.setPayment(payment);
        disbursement.setAmount(payment.getOwnerPayout());
        disbursement.setOwnerEmail(owner.getEmailAddress());
        disbursement.setOwnerName(trim(owner.getFirstName()) + " " + trim(owner.getLastName()));
        disbursement.setOwnerPhone(owner.getPhoneNumber());
        disbursement.setStatus(DisbursementStatus.PENDING);
        disbursementRepository.save(disbursement);
    }

    @Transactional
    public DisbursementResponse trigger(UUID disbursementId, DisbursementTriggerRequest request,
                                        Authentication authentication) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursement not found"));

        if (disbursement.getStatus() == DisbursementStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disbursement has already been completed");
        }

        disbursement.setStatus(DisbursementStatus.COMPLETED);
        disbursement.setTriggeredBy(authentication.getName());
        disbursement.setTriggeredAt(ZonedDateTime.now());
        disbursement.setReference(request != null ? request.reference() : null);
        disbursement.setNotes(request != null ? request.notes() : null);
        Disbursement saved = disbursementRepository.save(disbursement);

        notifyOwner(saved);

        return DisbursementResponse.from(saved);
    }

    @Transactional
    public DisbursementResponse markFailed(UUID disbursementId, DisbursementTriggerRequest request,
                                           Authentication authentication) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursement not found"));

        if (disbursement.getStatus() == DisbursementStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot fail a completed disbursement");
        }

        disbursement.setStatus(DisbursementStatus.FAILED);
        disbursement.setTriggeredBy(authentication.getName());
        disbursement.setTriggeredAt(ZonedDateTime.now());
        disbursement.setNotes(request != null ? request.notes() : null);
        return DisbursementResponse.from(disbursementRepository.save(disbursement));
    }

    @Transactional(readOnly = true)
    public List<DisbursementResponse> list(DisbursementStatus status) {
        List<Disbursement> results = status != null
                ? disbursementRepository.findByStatusOrderByCreatedAtDesc(status)
                : disbursementRepository.findAllByOrderByCreatedAtDesc();
        return results.stream().map(DisbursementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DisbursementResponse getByBooking(UUID bookingId) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for booking"));
        Disbursement disbursement = disbursementRepository.findByPaymentId(payment.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Disbursement not yet created for this booking — pickup may not have occurred"));
        return DisbursementResponse.from(disbursement);
    }

    @Transactional(readOnly = true)
    public DisbursementResponse getById(UUID id) {
        return DisbursementResponse.from(
                disbursementRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursement not found")));
    }

    private void notifyOwner(Disbursement disbursement) {
        try {
            notificationService.sendDisbursementPaid(disbursement);
        } catch (Exception e) {
            // email failure must not roll back the disbursement
        }
        try {
            Booking booking = disbursement.getPayment().getBooking();
            User owner = booking.getVehicle().getOwner();
            if (owner != null) {
                appNotificationService.createNotification(
                        owner,
                        "Payout Processed",
                        String.format("KES %.2f has been disbursed to you for booking of %s.",
                                disbursement.getAmount(),
                                booking.getVehicle().getModel() != null ? booking.getVehicle().getModel() : "your vehicle"),
                        "DISBURSEMENT_COMPLETED",
                        disbursement.getId(),
                        "DISBURSEMENT"
                );
            }
        } catch (Exception e) {
            // in-app notification failure must not roll back
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
