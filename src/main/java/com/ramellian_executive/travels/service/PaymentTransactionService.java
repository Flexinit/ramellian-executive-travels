package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.PaymentTransaction;
import com.ramellian_executive.travels.entity.PaymentTransactionType;
import com.ramellian_executive.travels.model.PaymentTransactionResponse;
import com.ramellian_executive.travels.repository.PaymentTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionService.class);

    private final PaymentTransactionRepository repository;

    /**
     * Creates a new ledger row for the given transaction.
     * Runs in its own transaction so a business-logic rollback does not lose the record.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction record(
            PaymentTransactionType type,
            String status,
            Double amount,
            String phone,
            String partyName,
            String email,
            String description,
            UUID bookingId,
            UUID paymentId,
            UUID disbursementId,
            UUID splitContributionId,
            String providerReference,
            String internalReference,
            String providerMessage) {
        return record(type, status, amount, phone, partyName, email, description,
                bookingId, paymentId, disbursementId, splitContributionId,
                providerReference, internalReference, providerMessage, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction record(
            PaymentTransactionType type,
            String status,
            Double amount,
            String phone,
            String partyName,
            String email,
            String description,
            UUID bookingId,
            UUID paymentId,
            UUID disbursementId,
            UUID splitContributionId,
            String providerReference,
            String internalReference,
            String providerMessage,
            String rawCallback) {
        try {
            PaymentTransaction tx = new PaymentTransaction();
            tx.setType(type);
            tx.setStatus(status);
            tx.setAmount(amount);
            tx.setPhone(phone);
            tx.setPartyName(partyName);
            tx.setEmail(email);
            tx.setDescription(description);
            tx.setBookingId(bookingId);
            tx.setPaymentId(paymentId);
            tx.setDisbursementId(disbursementId);
            tx.setSplitContributionId(splitContributionId);
            tx.setProviderReference(providerReference);
            tx.setInternalReference(internalReference);
            tx.setProviderMessage(providerMessage);
            tx.setRawCallback(rawCallback);
            return repository.save(tx);
        } catch (Exception e) {
            log.error("Failed to record payment transaction type={} paymentId={} disbursementId={}: {}",
                    type, paymentId, disbursementId, e.getMessage(), e);
            return null;
        }
    }

    /** Populates M-Pesa STK-specific fields on an existing ledger row. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMpesaFields(UUID id,
                                   String merchantRequestId,
                                   String checkoutRequestId,
                                   Integer resultCode,
                                   String resultDesc,
                                   String receiptNumber,
                                   String transactionDate,
                                   String mpesaPhone,
                                   String balance) {
        if (id == null) return;
        try {
            repository.findById(id).ifPresent(tx -> {
                tx.setMpesaMerchantRequestId(merchantRequestId);
                tx.setMpesaCheckoutRequestId(checkoutRequestId);
                tx.setMpesaResultCode(resultCode);
                tx.setMpesaResultDesc(resultDesc);
                tx.setMpesaReceiptNumber(receiptNumber);
                tx.setMpesaTransactionDate(transactionDate);
                tx.setMpesaPhoneNumber(mpesaPhone);
                tx.setMpesaBalance(balance);
                repository.save(tx);
            });
        } catch (Exception e) {
            log.error("Failed to update M-Pesa fields on transaction {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Updates the status and provider details on an existing ledger row.
     * Silently no-ops if the row is not found, so callers never need to handle absence.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID id, String status, String providerReference, String providerMessage) {
        if (id == null) return;
        try {
            repository.findById(id).ifPresent(tx -> {
                tx.setStatus(status);
                if (providerReference != null) tx.setProviderReference(providerReference);
                if (providerMessage != null)   tx.setProviderMessage(providerMessage);
                repository.save(tx);
            });
        } catch (Exception e) {
            log.error("Failed to update payment transaction {}: {}", id, e.getMessage(), e);
        }
    }

    // ── Query ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> search(
            PaymentTransactionType type,
            String status,
            String phone,
            UUID bookingId,
            ZonedDateTime from,
            ZonedDateTime to,
            Pageable pageable) {
        Specification<PaymentTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null)     predicates.add(cb.equal(root.get("type"), type));
            if (status != null && !status.isBlank())
                                  predicates.add(cb.equal(root.get("status"), status));
            if (phone != null && !phone.isBlank())
                                  predicates.add(cb.like(root.get("phone"), "%" + phone + "%"));
            if (bookingId != null) predicates.add(cb.equal(root.get("bookingId"), bookingId));
            if (from != null)     predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null)       predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable).map(PaymentTransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentTransactionResponse getById(UUID id) {
        return PaymentTransactionResponse.from(
                repository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Transaction not found")));
    }
}
