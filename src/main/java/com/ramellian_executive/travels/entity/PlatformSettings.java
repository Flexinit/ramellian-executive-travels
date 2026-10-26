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
