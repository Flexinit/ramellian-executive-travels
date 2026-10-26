package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.PlatformSettings;
import com.ramellian_executive.travels.model.PlatformSettingsRequest;
import com.ramellian_executive.travels.model.PlatformSettingsResponse;
import com.ramellian_executive.travels.repository.PlatformSettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;

    @Transactional(readOnly = true)
    public PlatformSettingsResponse get() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettingsResponse::from)
                .orElseGet(PlatformSettingsResponse::defaults);
    }

    @PostConstruct
    @Transactional
    public void seedDefaults() {
        if (repository.findFirstByOrderByCreatedAtDesc().isEmpty()) {
            repository.save(new PlatformSettings());
        }
    }

    @Transactional(readOnly = true)
    public double getCommissionRatePct() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::getCommissionRatePct)
                .orElse(15.0);
    }

    @Transactional(readOnly = true)
    public double getCommissionRatePct(long days) {
        PlatformSettings s = repository.findFirstByOrderByCreatedAtDesc().orElse(null);
        if (s == null) return days >= 2 ? 10.0 : 15.0;
        return days >= s.getMultiDayThresholdDays()
                ? s.getMultiDayCommissionRatePct()
                : s.getCommissionRatePct();
    }

    @Transactional(readOnly = true)
    public double getPesapalTransactionFeeRatePct() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::getPesapalTransactionFeeRatePct)
                .orElse(1.5);
    }

    @Transactional(readOnly = true)
    public double getDeliveryFlatFee() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::getDeliveryFlatFee)
                .orElse(0.0);
    }

    @Transactional(readOnly = true)
    public double getDriverFlatCharge() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::getDriverFlatCharge)
                .orElse(0.0);
    }

    @Transactional
    public PlatformSettingsResponse set(PlatformSettingsRequest request) {
        PlatformSettings settings = repository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(PlatformSettings::new);
        settings.setCommissionRatePct(request.commissionRatePct());
        if (request.multiDayCommissionRatePct() != null) {
            settings.setMultiDayCommissionRatePct(request.multiDayCommissionRatePct());
        }
        if (request.multiDayThresholdDays() != null && request.multiDayThresholdDays() >= 1) {
            settings.setMultiDayThresholdDays(request.multiDayThresholdDays());
        }
        if (request.pesapalTransactionFeeRatePct() != null) {
            settings.setPesapalTransactionFeeRatePct(request.pesapalTransactionFeeRatePct());
        }
        if (request.deliveryFlatFee() != null) {
            settings.setDeliveryFlatFee(request.deliveryFlatFee());
        }
        if (request.driverFlatCharge() != null) {
            settings.setDriverFlatCharge(request.driverFlatCharge());
        }
        if (request.botAssistantEnabled() != null) {
            settings.setBotAssistantEnabled(request.botAssistantEnabled());
        }
        if (request.pickupReminderEnabled() != null) {
            settings.setPickupReminderEnabled(request.pickupReminderEnabled());
        }
        if (request.pickupReminderRenterEnabled() != null) {
            settings.setPickupReminderRenterEnabled(request.pickupReminderRenterEnabled());
        }
        if (request.pickupReminderOwnerEnabled() != null) {
            settings.setPickupReminderOwnerEnabled(request.pickupReminderOwnerEnabled());
        }
        if (request.pickupReminderLeadMinutes() != null && request.pickupReminderLeadMinutes() >= 15) {
            settings.setPickupReminderLeadMinutes(request.pickupReminderLeadMinutes());
        }
        if (request.supportEmail() != null) settings.setSupportEmail(blank(request.supportEmail()) ? null : request.supportEmail().trim());
        if (request.supportPhone() != null) settings.setSupportPhone(blank(request.supportPhone()) ? null : request.supportPhone().trim());
        if (request.address() != null) settings.setAddress(blank(request.address()) ? null : request.address().trim());
        if (request.website() != null) settings.setWebsite(blank(request.website()) ? null : request.website().trim());
        if (request.facebook() != null) settings.setFacebook(blank(request.facebook()) ? null : request.facebook().trim());
        if (request.instagram() != null) settings.setInstagram(blank(request.instagram()) ? null : request.instagram().trim());
        if (request.whatsapp() != null) settings.setWhatsapp(blank(request.whatsapp()) ? null : request.whatsapp().trim());
        if (request.xTwitter() != null) settings.setXTwitter(blank(request.xTwitter()) ? null : request.xTwitter().trim());
        if (request.tiktok() != null) settings.setTiktok(blank(request.tiktok()) ? null : request.tiktok().trim());
        return PlatformSettingsResponse.from(repository.save(settings));
    }

    @Transactional
    public PlatformSettingsResponse toggleBot(boolean enabled) {
        PlatformSettings settings = repository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(PlatformSettings::new);
        settings.setBotAssistantEnabled(enabled);
        return PlatformSettingsResponse.from(repository.save(settings));
    }

    @Transactional(readOnly = true)
    public boolean isBotAssistantEnabled() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::isBotAssistantEnabled)
                .orElse(true);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    @Transactional(readOnly = true)
    public boolean isPickupReminderEnabled() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::isPickupReminderEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isPickupReminderRenterEnabled() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::isPickupReminderRenterEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isPickupReminderOwnerEnabled() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::isPickupReminderOwnerEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public int getPickupReminderLeadMinutes() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::getPickupReminderLeadMinutes)
                .orElse(60);
    }
}
