package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.PlatformSettings;
import com.ramellian_executive.travels.model.PlatformSettingsRequest;
import com.ramellian_executive.travels.model.PlatformSettingsResponse;
import com.ramellian_executive.travels.repository.PlatformSettingsRepository;
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

    @Transactional(readOnly = true)
    public double getCommissionRatePct() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(PlatformSettings::getCommissionRatePct)
                .orElse(2.0);
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
        if (request.pesapalTransactionFeeRatePct() != null) {
            settings.setPesapalTransactionFeeRatePct(request.pesapalTransactionFeeRatePct());
        }
        if (request.deliveryFlatFee() != null) {
            settings.setDeliveryFlatFee(request.deliveryFlatFee());
        }
        if (request.driverFlatCharge() != null) {
            settings.setDriverFlatCharge(request.driverFlatCharge());
        }
        return PlatformSettingsResponse.from(repository.save(settings));
    }
}
