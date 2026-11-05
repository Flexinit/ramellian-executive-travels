package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PlatformSettingsRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double commissionRatePct,
        @DecimalMin("0.0") @DecimalMax("100.0") Double multiDayCommissionRatePct,
        Integer multiDayThresholdDays,
        @DecimalMin("0.0") @DecimalMax("100.0") Double pesapalTransactionFeeRatePct,
        @DecimalMin("0.0") Double deliveryFlatFee,
        Boolean deliveryFeeEnabled,
        @DecimalMin("0.0") Double driverFlatCharge,
        Boolean driverChargeEnabled,
        @DecimalMin("0.0") Double mpesaStkFee,
        Boolean mpesaStkFeeEnabled,
        @DecimalMin("0.0") Double mpesaB2cFee,
        Boolean mpesaB2cFeeEnabled,
        Boolean pesapalTransactionFeeEnabled,
        Integer overstayGracePeriodHours,
        @DecimalMin("1.0") Double overstayRateMultiplier,
        Boolean botAssistantEnabled,
        Boolean pickupReminderEnabled,
        Boolean pickupReminderRenterEnabled,
        Boolean pickupReminderOwnerEnabled,
        Integer pickupReminderLeadMinutes,
        String supportEmail,
        String supportPhone,
        String address,
        String website,
        String facebook,
        String instagram,
        String whatsapp,
        String xTwitter,
        String tiktok,
        String disbursementProvider
) {}
