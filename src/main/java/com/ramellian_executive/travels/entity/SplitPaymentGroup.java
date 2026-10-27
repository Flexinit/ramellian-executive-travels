package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "split_payment_groups", indexes = {
        @Index(name = "idx_split_group_booking", columnList = "booking_id"),
        @Index(name = "idx_split_group_status",  columnList = "status")
})
public class SplitPaymentGroup extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "total_amount", nullable = false)
    private double totalAmount;

    @Column(name = "platform_fee", nullable = false)
    private double platformFee;

    @Column(name = "processing_fee", nullable = false)
    private double processingFee;

    @Column(name = "delivery_fee", nullable = false)
    private double deliveryFee;

    @Column(name = "driver_charge", nullable = false)
    private double driverCharge;

    @Column(name = "owner_payout", nullable = false)
    private double ownerPayout;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SplitPaymentGroupStatus status = SplitPaymentGroupStatus.PENDING;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "expires_at")
    private ZonedDateTime expiresAt;

    /** Set once all contributions are paid and the master Payment is created. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;
}
