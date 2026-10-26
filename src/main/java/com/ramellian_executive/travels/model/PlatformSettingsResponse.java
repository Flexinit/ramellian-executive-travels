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
        boolean botAssistantEnabled,
        boolean pickupReminderEnabled,
        boolean pickupReminderRenterEnabled,
        boolean pickupReminderOwnerEnabled,
        int pickupReminderLeadMinutes,
        String supportEmail,
        String supportPhone,
        String address,
        String website,
        String facebook,
        String instagram,
        String whatsapp,
        String xTwitter,
        String tiktok
) {
    public static PlatformSettingsResponse from(PlatformSettings s) {
        return new PlatformSettingsResponse(s.getId(), s.getCommissionRatePct(),
                s.getMultiDayCommissionRatePct(), s.getMultiDayThresholdDays(),
                s.getPesapalTransactionFeeRatePct(), s.getDeliveryFlatFee(), s.getDriverFlatCharge(),
                s.isBotAssistantEnabled(),
                s.isPickupReminderEnabled(), s.isPickupReminderRenterEnabled(),
                s.isPickupReminderOwnerEnabled(), s.getPickupReminderLeadMinutes(),
                s.getSupportEmail(), s.getSupportPhone(), s.getAddress(),
                s.getWebsite(), s.getFacebook(), s.getInstagram(), s.getWhatsapp(),
                s.getXTwitter(), s.getTiktok());
    }

    public static PlatformSettingsResponse defaults() {
        return new PlatformSettingsResponse(null, 15.0, 10.0, 2, 1.5, 0.0, 0.0,
                true, true, true, true, 60,
                null, null, null, null, null, null, null, null, null);
    }
}
