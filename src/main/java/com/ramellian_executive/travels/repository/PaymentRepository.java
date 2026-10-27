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
    List<Payment> findAllByBookingIdOrderByCreatedAtAsc(UUID bookingId);
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

    // Returns single row: [grossRevenue, platformFee, ownerPayout, transactionFee, deliveryFee, driverCharge, count]
    @Query("SELECT SUM(p.amount), SUM(p.platformFee), SUM(p.ownerPayout), SUM(p.transactionFee), SUM(p.deliveryFee), SUM(p.driverCharge), COUNT(p) FROM Payment p WHERE p.status = :status AND p.createdAt >= :from AND p.createdAt < :to")
    List<Object[]> financialTotals(@Param("status") PaymentStatus status, @Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);

    // Returns [[status, count], ...]
    @Query("SELECT p.status, COUNT(p) FROM Payment p WHERE p.createdAt >= :from AND p.createdAt < :to GROUP BY p.status")
    List<Object[]> countByStatusBetween(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);

    // Native query: daily breakdown [date, gross, platform_fee, owner_payout, count]
    @Query(value = "SELECT (p.created_at AT TIME ZONE 'UTC')::date AS day, COALESCE(SUM(p.amount),0) AS gross, COALESCE(SUM(p.platform_fee),0) AS pf, COALESCE(SUM(p.owner_payout),0) AS op, COUNT(p.id) AS cnt FROM payment p WHERE p.status = 'SUCCESSFUL' AND p.created_at >= :from AND p.created_at < :to GROUP BY 1 ORDER BY 1", nativeQuery = true)
    List<Object[]> dailyEarningsBetween(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to);

    // Native query: top vehicles [vehicleId, model, regNum, bookingCount, gross, platformFee]
    @Query(value = "SELECT b.vehicle_id::text, v.model, v.registration_number, COUNT(p.id) AS booking_count, COALESCE(SUM(p.amount),0) AS gross, COALESCE(SUM(p.platform_fee),0) AS pf FROM payment p JOIN booking b ON p.booking_id = b.id JOIN vehicle v ON b.vehicle_id = v.id WHERE p.status = 'SUCCESSFUL' AND p.created_at >= :from AND p.created_at < :to GROUP BY b.vehicle_id, v.model, v.registration_number ORDER BY SUM(p.platform_fee) DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> topVehicles(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to, @Param("limit") int limit);

    // Native query: top owners [ownerId, firstName, lastName, email, vehicleCount, bookingCount, gross, platformFee, ownerPayout]
    @Query(value = "SELECT u.id::text, u.first_name, u.last_name, u.email_address, COUNT(DISTINCT v.id) AS vehicle_count, COUNT(p.id) AS booking_count, COALESCE(SUM(p.amount),0) AS gross, COALESCE(SUM(p.platform_fee),0) AS pf, COALESCE(SUM(p.owner_payout),0) AS op FROM payment p JOIN booking b ON p.booking_id = b.id JOIN vehicle v ON b.vehicle_id = v.id JOIN app_users u ON v.owner_id = u.id WHERE p.status = 'SUCCESSFUL' AND p.created_at >= :from AND p.created_at < :to GROUP BY u.id, u.first_name, u.last_name, u.email_address ORDER BY SUM(p.amount) DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> topOwners(@Param("from") ZonedDateTime from, @Param("to") ZonedDateTime to, @Param("limit") int limit);
}
