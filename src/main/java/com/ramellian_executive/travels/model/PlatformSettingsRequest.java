package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PlatformSettingsRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double commissionRatePct,
        @DecimalMin("0.0") @DecimalMax("100.0") Double pesapalTransactionFeeRatePct,
        @DecimalMin("0.0") Double deliveryFlatFee,
        @DecimalMin("0.0") Double driverFlatCharge
) {}
