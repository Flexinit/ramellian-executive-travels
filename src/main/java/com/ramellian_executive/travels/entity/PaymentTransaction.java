package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Flat, queryable ledger of every M-Pesa and PesaPal transaction (inbound and outbound).
 * One row per transaction; status is updated in-place as the provider confirms.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_ptx_type",            columnList = "type"),
    @Index(name = "idx_ptx_status",          columnList = "status"),
    @Index(name = "idx_ptx_booking",         columnList = "booking_id"),
    @Index(name = "idx_ptx_payment",         columnList = "payment_id"),
    @Index(name = "idx_ptx_disbursement",    columnList = "disbursement_id"),
    @Index(name = "idx_ptx_phone",           columnList = "phone"),
    @Index(name = "idx_ptx_provider_ref",    columnList = "provider_reference")
})
public class PaymentTransaction extends AbstractEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PaymentTransactionType type;

    /** PENDING | SUCCESSFUL | FAILED */
    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "currency", nullable = false)
    private String currency = "KES";

    /** M-Pesa receipt number, PesaPal transaction receipt, or B2C conversation ID. */
    @Column(name = "provider_reference")
    private String providerReference;

    /** Our internal reference sent to the provider (merchant reference, batch number, etc.). */
    @Column(name = "internal_reference")
    private String internalReference;

    /** Actual phone confirmed by M-Pesa callback (PhoneNumber from CallbackMetadata). */
    @Column(name = "phone")
    private String phone;

    /** Renter's stored phone number (from user profile at time of payment). */
    @Column(name = "renter_phone")
    private String renterPhone;

    /** Name from M-Pesa callback (or payer name from split request). */
    @Column(name = "party_name")
    private String partyName;

    /** Renter's stored full name (from user profile at time of payment). */
    @Column(name = "renter_name")
    private String renterName;

    @Column(name = "email")
    private String email;

    @Column(name = "description")
    private String description;

    /** Booking this transaction relates to (denormalised for fast filtering). */
    @Column(name = "booking_id")
    private UUID bookingId;

    /** FK to Payment row (C2B transactions). */
    @Column(name = "payment_id")
    private UUID paymentId;

    /** FK to Disbursement row (B2C transactions). */
    @Column(name = "disbursement_id")
    private UUID disbursementId;

    /** FK to SplitPaymentContribution (split STK transactions). */
    @Column(name = "split_contribution_id")
    private UUID splitContributionId;

    /** Raw result description / error message from the provider. */
    @Column(name = "provider_message", columnDefinition = "TEXT")
    private String providerMessage;

    /** Full raw callback payload from Safaricom / PesaPal (stored for audit). */
    @Column(name = "raw_callback", columnDefinition = "TEXT")
    private String rawCallback;

    // ── M-Pesa STK Push callback fields ──────────────────────────────────────

    @Column(name = "mpesa_merchant_request_id")
    private String mpesaMerchantRequestId;

    @Column(name = "mpesa_checkout_request_id")
    private String mpesaCheckoutRequestId;

    @Column(name = "mpesa_result_code")
    private Integer mpesaResultCode;

    @Column(name = "mpesa_result_desc")
    private String mpesaResultDesc;

    /** M-Pesa receipt number (from CallbackMetadata). */
    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;

    /** Transaction date as returned by Safaricom (yyyyMMddHHmmss). */
    @Column(name = "mpesa_transaction_date")
    private String mpesaTransactionDate;

    /** Phone number from CallbackMetadata (may differ from the initiating phone). */
    @Column(name = "mpesa_phone_number")
    private String mpesaPhoneNumber;

    @Column(name = "mpesa_balance")
    private String mpesaBalance;
}
