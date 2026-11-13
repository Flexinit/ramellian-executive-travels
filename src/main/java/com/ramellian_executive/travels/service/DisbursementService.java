package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.DisbursementResponse;
import com.ramellian_executive.travels.model.DisbursementTriggerRequest;
import com.ramellian_executive.travels.model.ManualDisbursementRequest;
import com.ramellian_executive.travels.model.UpdateDisbursementRequest;
import com.ramellian_executive.travels.repository.DisbursementRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.repository.PaymentTransactionRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisbursementService {

    private static final Logger log = LoggerFactory.getLogger(DisbursementService.class);

    private final DisbursementRepository disbursementRepository;
    private final PaymentRepository paymentRepository;
    private final VerificationNotificationService notificationService;
    private final NotificationService appNotificationService;
    private final PesapalClient pesapalClient;
    private final DarajaClient darajaClient;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PlatformSettingsService platformSettingsService;
    private final ObjectMapper objectMapper;


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
        if (disbursement.getStatus() == DisbursementStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Disbursement is already processing — use /sync to check status");
        }
        // Allow re-triggering FAILED disbursements
        if (disbursement.getStatus() == DisbursementStatus.FAILED) {
            disbursement.setStatus(DisbursementStatus.PENDING);
            disbursement.setReference(null);
        }

        if (disbursement.getOwnerPhone() == null || disbursement.getOwnerPhone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Owner phone number is required for Pesapal disbursement");
        }

        String narration = "Payout for booking "
                + (disbursement.getPayment() != null && disbursement.getPayment().getBooking() != null
                ? disbursement.getPayment().getBooking().getId().toString() : disbursement.getId().toString());

        String reference;
        String usedProvider;
        String disbursementProvider = platformSettingsService.getDisbursementProvider();
        if ("DARAJA".equalsIgnoreCase(disbursementProvider) && darajaClient.isConfigured()) {
            DarajaClient.B2cResult b2c = darajaClient.b2c(
                    java.util.UUID.randomUUID().toString(),
                    disbursement.getOwnerPhone(),
                    disbursement.getOwnerName() != null ? disbursement.getOwnerName() : "",
                    disbursement.getAmount(),
                    narration);
            reference = b2c.originatorConversationId();
            usedProvider = "DARAJA";
            log.info("Daraja B2C triggered for disbursement {} — originatorConversationId={}", disbursement.getId(), reference);
        } else {
            PesapalClient.PesapalDisbursementBatch batch = pesapalClient.disburse(
                    disbursement.getId().toString(),
                    disbursement.getOwnerPhone(),
                    disbursement.getOwnerName() != null ? disbursement.getOwnerName() : "",
                    disbursement.getAmount(),
                    narration);
            reference = batch.batchNumber();
            usedProvider = "PESAPAL";
        }

        disbursement.setStatus(DisbursementStatus.PROCESSING);
        disbursement.setTriggeredBy(authentication.getName());
        disbursement.setTriggeredAt(ZonedDateTime.now());
        disbursement.setReference(reference);
        disbursement.setProvider(usedProvider);
        disbursement.setNotes(request != null ? request.notes() : null);

        Disbursement saved = disbursementRepository.save(disbursement);

        PaymentTransactionType txType = "DARAJA".equalsIgnoreCase(usedProvider)
                ? PaymentTransactionType.MPESA_B2C : PaymentTransactionType.PESAPAL_B2C;
        UUID bookingIdForTx = (saved.getPayment() != null && saved.getPayment().getBooking() != null)
                ? saved.getPayment().getBooking().getId() : null;
        UUID paymentIdForTx = saved.getPayment() != null ? saved.getPayment().getId() : null;
        paymentTransactionService.record(
                txType, "PENDING",
                saved.getAmount(),
                saved.getOwnerPhone(), saved.getOwnerName(), saved.getOwnerEmail(),
                narration,
                bookingIdForTx, paymentIdForTx, saved.getId(), null,
                null, reference, null);

        return DisbursementResponse.from(saved);
    }

    @Transactional
    public DisbursementResponse syncStatus(UUID disbursementId, Authentication authentication) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursement not found"));

        if (disbursement.getStatus() == DisbursementStatus.COMPLETED) {
            return DisbursementResponse.from(disbursement);
        }
        if ("DARAJA".equalsIgnoreCase(disbursement.getProvider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Daraja disbursements are updated via B2C callback — manual sync not supported");
        }
        if (disbursement.getReference() == null || disbursement.getReference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No Pesapal batch number on record — trigger disbursement first");
        }

        PesapalClient.PesapalDisbursementStatus pesapalStatus =
                pesapalClient.getDisbursementStatus(disbursement.getReference());

        if ("COMPLETED".equals(pesapalStatus.status())) {
            disbursement.setStatus(DisbursementStatus.COMPLETED);
            Disbursement saved = disbursementRepository.save(disbursement);
            updateTxLedger(saved, "SUCCESSFUL", null);
            notifyOwner(saved);
            return DisbursementResponse.from(saved);
        }
        if ("FAILED".equals(pesapalStatus.status())) {
            disbursement.setStatus(DisbursementStatus.FAILED);
            Disbursement saved = disbursementRepository.save(disbursement);
            updateTxLedger(saved, "FAILED", null);
            return DisbursementResponse.from(saved);
        }

        return DisbursementResponse.from(disbursement);
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

    /** Polls Pesapal every 10 minutes for any disbursement still in PROCESSING state. */
    @Transactional
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void autoSyncProcessingDisbursements() {
        List<Disbursement> processing = disbursementRepository
                .findByStatusOrderByCreatedAtDesc(DisbursementStatus.PROCESSING);
        if (processing.isEmpty()) return;
        log.info("Auto-syncing {} PROCESSING disbursement(s) with Pesapal", processing.size());
        for (Disbursement disbursement : processing) {
            try {
                if (disbursement.getReference() == null || disbursement.getReference().isBlank()) continue;
                if ("DARAJA".equalsIgnoreCase(disbursement.getProvider())) continue;
                PesapalClient.PesapalDisbursementStatus ps =
                        pesapalClient.getDisbursementStatus(disbursement.getReference());
                if ("COMPLETED".equals(ps.status())) {
                    disbursement.setStatus(DisbursementStatus.COMPLETED);
                    disbursementRepository.save(disbursement);
                    updateTxLedger(disbursement, "SUCCESSFUL", null);
                    notifyOwner(disbursement);
                    log.info("Disbursement {} marked COMPLETED", disbursement.getId());
                } else if ("FAILED".equals(ps.status())) {
                    disbursement.setStatus(DisbursementStatus.FAILED);
                    disbursementRepository.save(disbursement);
                    updateTxLedger(disbursement, "FAILED", null);
                    log.warn("Disbursement {} marked FAILED by Pesapal", disbursement.getId());
                }
            } catch (Exception e) {
                log.error("Failed to auto-sync disbursement {}", disbursement.getId(), e);
            }
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void handleB2cCallback(Map<String, Object> payload) {
        try {
            Map<String, Object> result = (Map<String, Object>) payload.get("Result");
            if (result == null) { log.warn("B2C callback with no Result block"); return; }

            int resultCode      = ((Number) result.get("ResultCode")).intValue();
            String originatorId = String.valueOf(result.get("OriginatorConversationID"));
            String transactionId= String.valueOf(result.getOrDefault("TransactionID", ""));
            String resultDesc   = String.valueOf(result.getOrDefault("ResultDesc", ""));
            String receipt      = extractB2cReceipt(result);

            log.info("B2C callback — originatorId={} resultCode={} receipt={}", originatorId, resultCode, receipt);

            Disbursement disbursement = findDisbursementByOriginatorId(originatorId);
            if (disbursement == null) return;

            applyB2cResult(disbursement, resultCode, transactionId, resultDesc, receipt);
            updateB2cLedger(disbursement.getId(), resultCode, resultDesc, receipt);
        } catch (Exception e) {
            log.error("Error processing B2C callback: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractB2cReceipt(Map<String, Object> result) {
        try {
            Map<String, Object> params = (Map<String, Object>) result.get("ResultParameters");
            if (params == null) return null;
            List<Map<String, Object>> list = (List<Map<String, Object>>) params.get("ResultParameter");
            return DarajaClient.extractResultParam(list, "TransactionReceipt");
        } catch (Exception e) { return null; }
    }

    private Disbursement findDisbursementByOriginatorId(String originatorId) {
        try {
            UUID id = UUID.fromString(originatorId);
            Disbursement d = disbursementRepository.findById(id).orElse(null);
            if (d == null) log.warn("B2C callback for unknown disbursement: {}", id);
            return d;
        } catch (Exception e) {
            log.warn("B2C callback has non-UUID OriginatorConversationID: {}", originatorId);
            return null;
        }
    }

    private void applyB2cResult(Disbursement d, int resultCode, String transactionId,
                                 String resultDesc, String receipt) {
        if (resultCode == 0) {
            d.setStatus(DisbursementStatus.COMPLETED);
            if (!transactionId.isBlank()) d.setReference(transactionId);
            disbursementRepository.save(d);
            updateTxLedger(d, "SUCCESSFUL", receipt);
            notifyOwner(d);
            log.info("Disbursement {} COMPLETED via B2C callback", d.getId());
        } else {
            d.setStatus(DisbursementStatus.FAILED);
            disbursementRepository.save(d);
            updateTxLedger(d, "FAILED", resultDesc);
            log.warn("Disbursement {} FAILED via B2C callback — ResultCode={}", d.getId(), resultCode);
        }
    }

    private void updateB2cLedger(UUID disbursementId, int resultCode, String resultDesc, String receipt) {
        paymentTransactionRepository.findByDisbursementId(disbursementId).ifPresent(tx ->
                paymentTransactionService.updateMpesaFields(tx.getId(),
                        null, null, resultCode, resultDesc, receipt, null, null, null));
    }

    private String safeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return null; }
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

    @Transactional
    public DisbursementResponse createManual(UUID bookingId, ManualDisbursementRequest request,
                                             Authentication authentication) {
        Payment payment = paymentRepository
                .findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No successful rental payment found for this booking"));
        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment has not been completed — payout cannot be created");
        }
        if (disbursementRepository.existsByPaymentId(payment.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A disbursement already exists for this booking — use /trigger or /update instead");
        }

        Booking booking = payment.getBooking();
        User owner = booking.getVehicle().getOwner();

        double amount = (request != null && request.amount() != null && request.amount() > 0)
                ? request.amount()
                : payment.getOwnerPayout();
        String phone = (request != null && request.ownerPhone() != null && !request.ownerPhone().isBlank())
                ? request.ownerPhone().trim()
                : owner.getPhoneNumber();

        Disbursement disbursement = new Disbursement();
        disbursement.setPayment(payment);
        disbursement.setAmount(amount);
        disbursement.setOwnerEmail(owner.getEmailAddress());
        disbursement.setOwnerName(trim(owner.getFirstName()) + " " + trim(owner.getLastName()));
        disbursement.setOwnerPhone(phone);
        disbursement.setStatus(DisbursementStatus.PENDING);
        disbursement.setTriggeredBy(authentication.getName());
        disbursement.setNotes(request != null ? request.notes() : null);
        return DisbursementResponse.from(disbursementRepository.save(disbursement));
    }

    @Transactional
    public DisbursementResponse update(UUID id, UpdateDisbursementRequest request,
                                       Authentication authentication) {
        Disbursement disbursement = disbursementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursement not found"));
        if (disbursement.getStatus() == DisbursementStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot edit a completed disbursement");
        }
        if (disbursement.getStatus() == DisbursementStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot edit a disbursement that is currently processing");
        }
        if (request.ownerPhone() != null && !request.ownerPhone().isBlank()) {
            disbursement.setOwnerPhone(request.ownerPhone().trim());
        }
        if (request.amount() != null && request.amount() > 0) {
            disbursement.setAmount(request.amount());
        }
        if (request.notes() != null) {
            disbursement.setNotes(request.notes().isBlank() ? null : request.notes().trim());
        }
        return DisbursementResponse.from(disbursementRepository.save(disbursement));
    }

    @Transactional(readOnly = true)
    public List<DisbursementResponse> listForOwner(Authentication authentication) {
        return disbursementRepository
                .findByOwnerEmailIgnoreCaseOrderByCreatedAtDesc(authentication.getName())
                .stream().map(DisbursementResponse::from).toList();
    }

    private void updateTxLedger(Disbursement disbursement, String status, String message) {
        try {
            paymentTransactionRepository.findByDisbursementId(disbursement.getId())
                    .ifPresent(tx -> paymentTransactionService.updateStatus(tx.getId(), status, null, message));
        } catch (Exception e) {
            log.warn("Failed to update payment transaction ledger for disbursement {}: {}",
                    disbursement.getId(), e.getMessage());
        }
    }

    private static String trim(String s) {
        return s != null ? s.trim() : "";
    }
}
