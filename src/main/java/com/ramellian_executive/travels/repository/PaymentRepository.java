package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Payment;
import com.ramellian_executive.travels.entity.PaymentChargeType;
import com.ramellian_executive.travels.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBookingId(UUID bookingId);
    Optional<Payment> findByProviderMerchantReference(String merchantReference);
    boolean existsByTransactionReference(String transactionReference);
    boolean existsByBookingIdAndStatus(UUID bookingId, PaymentStatus status);
    List<Payment> findAllByBookingInAndStatus(List<com.ramellian_executive.travels.entity.Booking> bookings,
                                              PaymentStatus status);
    List<Payment> findAllByProviderOrderTrackingId(String providerOrderTrackingId);
    Optional<Payment> findFirstByProviderMerchantReferenceStartingWith(String prefix);
    Optional<Payment> findByBookingIdAndChargeType(UUID bookingId, PaymentChargeType chargeType);
}
