package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment", indexes = {
    @Index(name = "idx_payment_booking", columnList = "booking_id"),
    @Index(name = "idx_payment_status",  columnList = "status")
})
public class Payment extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "platform_fee", nullable = false)
    private Double platformFee = 0.0;

    @Column(name = "transaction_fee", nullable = false)
    private Double transactionFee = 0.0;

    @Column(name = "delivery_fee", nullable = false)
    private Double deliveryFee = 0.0;

    @Column(name = "driver_charge", nullable = false)
    private Double driverCharge = 0.0;

    @Column(name = "owner_payout", nullable = false)
    private Double ownerPayout = 0.0;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_order_tracking_id", unique = true)
    private String providerOrderTrackingId;

    @Column(name = "provider_merchant_reference", unique = true)
    private String providerMerchantReference;

    @Column(name = "provider_receipt")
    private String providerReceipt;

    @Column(name = "receipt_file_name")
    private String receiptFileName;

    @Column(name = "admin_receipt_file_name")
    private String adminReceiptFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false)
    private PaymentChargeType chargeType = PaymentChargeType.RENTAL;

}
