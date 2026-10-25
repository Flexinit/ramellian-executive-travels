package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.PlatformSettings;

import java.util.UUID;

public record PlatformSettingsResponse(
        UUID id,
        double commissionRatePct,
        double multiDayCommissionRatePct,
        int multiDayThresholdDays,
        double pesapalTransactionFeeRatePct,
        double deliveryFlatFee,
        double driverFlatCharge,
        boolean botAssistantEnabled
) {
    public static PlatformSettingsResponse from(PlatformSettings s) {
        return new PlatformSettingsResponse(s.getId(), s.getCommissionRatePct(),
                s.getMultiDayCommissionRatePct(), s.getMultiDayThresholdDays(),
                s.getPesapalTransactionFeeRatePct(), s.getDeliveryFlatFee(), s.getDriverFlatCharge(),
                s.isBotAssistantEnabled());
    }

    public static PlatformSettingsResponse defaults() {
        return new PlatformSettingsResponse(null, 15.0, 10.0, 2, 1.5, 0.0, 0.0, true);
    }
}
