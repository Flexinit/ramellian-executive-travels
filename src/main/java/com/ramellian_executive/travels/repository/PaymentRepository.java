package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Payment;
import com.ramellian_executive.travels.entity.PaymentChargeType;
import com.ramellian_executive.travels.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
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
    boolean existsByBookingIdAndStatusAndProviderOrderTrackingIdIsNotNull(UUID bookingId, PaymentStatus status);

    @Query("""
            SELECT p FROM Payment p
            WHERE p.booking.vehicle.owner.emailAddress = :ownerEmail
              AND p.status = :status
              AND (:vehicleId IS NULL OR p.booking.vehicle.id = :vehicleId)
              AND (:from IS NULL OR p.createdAt >= :from)
              AND (:to IS NULL OR p.createdAt <= :to)
            ORDER BY p.createdAt DESC
            """)
    List<Payment> findOwnerPayments(@Param("ownerEmail") String ownerEmail,
                                    @Param("status") PaymentStatus status,
                                    @Param("vehicleId") UUID vehicleId,
                                    @Param("from") ZonedDateTime from,
                                    @Param("to") ZonedDateTime to);
}
