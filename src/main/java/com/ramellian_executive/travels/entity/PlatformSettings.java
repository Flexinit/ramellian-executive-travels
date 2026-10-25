package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "platform_settings")
public class PlatformSettings extends AbstractEntity {

    @Column(name = "commission_rate_pct", nullable = false)
    private double commissionRatePct = 2.0;

    @Column(name = "pesapal_transaction_fee_rate_pct", nullable = false)
    private double pesapalTransactionFeeRatePct = 1.5;

    @Column(name = "delivery_flat_fee", nullable = false)
    private double deliveryFlatFee = 0.0;

    @Column(name = "driver_flat_charge", nullable = false)
    private double driverFlatCharge = 0.0;

    @Column(name = "bot_assistant_enabled", nullable = false)
    private boolean botAssistantEnabled = true;
}
