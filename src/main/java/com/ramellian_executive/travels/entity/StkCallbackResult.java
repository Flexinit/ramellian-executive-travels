package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "stk_callback_results", indexes = {
    @Index(name = "idx_stk_callback_checkout",  columnList = "checkout_request_id"),
    @Index(name = "idx_stk_callback_payment",   columnList = "payment_id"),
    @Index(name = "idx_stk_callback_booking",   columnList = "booking_id")
})
public class StkCallbackResult extends AbstractEntity {

    @Column(name = "merchant_request_id")
    private String merchantRequestId;

    @Column(name = "checkout_request_id")
    private String checkoutRequestId;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "transaction_date")
    private String transactionDate;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "balance")
    private String balance;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "received_at", nullable = false)
    private ZonedDateTime receivedAt;
}
