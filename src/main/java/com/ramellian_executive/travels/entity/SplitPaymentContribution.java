package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "split_payment_contributions", indexes = {
        @Index(name = "idx_split_contribution_group",    columnList = "split_group_id"),
        @Index(name = "idx_split_contribution_tracking", columnList = "provider_order_tracking_id"),
        @Index(name = "idx_split_contribution_status",   columnList = "status")
})
public class SplitPaymentContribution extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "split_group_id", nullable = false)
    private SplitPaymentGroup splitGroup;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payer_phone", nullable = false)
    private String payerPhone;

    @Column(name = "payer_email")
    private String payerEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_user_id")
    private User payerUser;

    @Column(name = "amount", nullable = false)
    private double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SplitContributionStatus status = SplitContributionStatus.PENDING;

    @Column(name = "provider_order_tracking_id", unique = true)
    private String providerOrderTrackingId;

    @Column(name = "provider_merchant_request_id")
    private String providerMerchantRequestId;

    @Column(name = "provider_receipt")
    private String providerReceipt;

    @Column(name = "payment_method")
    private String paymentMethod;
}
