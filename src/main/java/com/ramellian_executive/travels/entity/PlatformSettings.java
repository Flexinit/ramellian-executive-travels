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
    private double commissionRatePct = 15.0;

    @Column(name = "multi_day_commission_rate_pct", nullable = false, columnDefinition = "float8 not null default 10.0")
    private double multiDayCommissionRatePct = 10.0;

    @Column(name = "multi_day_threshold_days", nullable = false, columnDefinition = "integer not null default 2")
    private int multiDayThresholdDays = 2;

    @Column(name = "pesapal_transaction_fee_rate_pct", nullable = false)
    private double pesapalTransactionFeeRatePct = 1.5;

    @Column(name = "mpesa_stk_fee", nullable = false, columnDefinition = "float8 not null default 33.0")
    private double mpesaStkFee = 33.0;

    @Column(name = "mpesa_b2c_fee", nullable = false, columnDefinition = "float8 not null default 50.0")
    private double mpesaB2cFee = 50.0;

    /** Hours of overstay after which a full extra day is charged. Default: any overstay = 1 day. */
    @Column(name = "overstay_grace_period_hours", nullable = false, columnDefinition = "integer not null default 1")
    private int overstayGracePeriodHours = 1;

    /** Rate multiplier applied to daily rate for overstay days. Default: 1.5x (50% penalty). */
    @Column(name = "overstay_rate_multiplier", nullable = false, columnDefinition = "float8 not null default 1.5")
    private double overstayRateMultiplier = 1.5;

    @Column(name = "delivery_flat_fee", nullable = false)
    private double deliveryFlatFee = 0.0;

    @Column(name = "driver_flat_charge", nullable = false)
    private double driverFlatCharge = 0.0;

    @Column(name = "bot_assistant_enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean botAssistantEnabled = true;

    @Column(name = "pickup_reminder_enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean pickupReminderEnabled = true;

    @Column(name = "pickup_reminder_renter_enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean pickupReminderRenterEnabled = true;

    @Column(name = "pickup_reminder_owner_enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean pickupReminderOwnerEnabled = true;

    @Column(name = "pickup_reminder_lead_minutes", nullable = false, columnDefinition = "integer not null default 60")
    private int pickupReminderLeadMinutes = 60;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "support_phone")
    private String supportPhone;

    @Column(name = "address")
    private String address;

    @Column(name = "website")
    private String website;

    @Column(name = "facebook")
    private String facebook;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "x_twitter")
    private String xTwitter;

    @Column(name = "tiktok")
    private String tiktok;
}
