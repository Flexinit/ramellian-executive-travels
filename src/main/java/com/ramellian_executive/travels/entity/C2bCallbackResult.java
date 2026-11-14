package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "c2b_callback_results", indexes = {
    @Index(name = "idx_c2b_callback_trans_id",  columnList = "transaction_id"),
    @Index(name = "idx_c2b_callback_bill_ref",  columnList = "bill_ref_number"),
    @Index(name = "idx_c2b_callback_payment",   columnList = "payment_id"),
    @Index(name = "idx_c2b_callback_booking",   columnList = "booking_id")
})
public class C2bCallbackResult extends AbstractEntity {

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "trans_time")
    private String transTime;

    @Column(name = "trans_amount")
    private Double transAmount;

    @Column(name = "business_short_code")
    private String businessShortCode;

    @Column(name = "bill_ref_number")
    private String billRefNumber;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "org_account_balance")
    private String orgAccountBalance;

    @Column(name = "third_party_trans_id")
    private String thirdPartyTransId;

    @Column(name = "msisdn")
    private String msisdn;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "received_at", nullable = false)
    private ZonedDateTime receivedAt;
}
