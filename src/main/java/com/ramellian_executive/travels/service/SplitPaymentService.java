package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.SplitPaymentRequest;
import com.ramellian_executive.travels.model.SplitPaymentResponse;
import com.ramellian_executive.travels.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SplitPaymentService {

    private static final Logger log = LoggerFactory.getLogger(SplitPaymentService.class);
    private static final int DEFAULT_EXPIRY_MINUTES = 30;

    private final BookingRepository bookingRepository;
    private final BookingContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final SplitPaymentGroupRepository groupRepository;
    private final SplitPaymentContributionRepository contributionRepository;
    private final DarajaClient darajaClient;
    private final PlatformSettingsService platformSettingsService;
    private final BookingService bookingService;
    private final ReceiptService receiptService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Transactional
    public SplitPaymentResponse initiate(UUID bookingId, SplitPaymentRequest request,
                                         Authentication authentication) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the booking owner can initiate split payment");
        }

        BookingContract contract = contractRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Accept the contract before initiating payment"));
        if (contract.getAcceptedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Accept the contract before initiating payment");
        }

        // Block if already paid or split in progress
        if (paymentRepository.existsByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESSFUL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is already paid");
        }
        groupRepository.findByBookingIdAndStatusIn(bookingId,
                        List.of(SplitPaymentGroupStatus.PENDING, SplitPaymentGroupStatus.PARTIAL))
                .ifPresent(g -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A split payment is already in progress for this booking"); });

        // Calculate totals (same logic as MpesaPaymentService)
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle().getPricePerDay() * days;
        double platformFee = base * (platformSettingsService.getCommissionRatePct(days) / 100.0);
        double processingFee = platformSettingsService.getMpesaStkFee()
                + platformSettingsService.getMpesaB2cFee();
        double deliveryFee = booking.isDeliveryRequested() ? platformSettingsService.getDeliveryFlatFee() : 0.0;
        double driverCharge = booking.getBookingMode() == BookingMode.CHAUFFEURED
                ? platformSettingsService.getDriverFlatCharge() : 0.0;
        double totalCharge = base + processingFee + deliveryFee + driverCharge;
        double ownerPayout = base - platformFee;

        // Validate split amounts add up to total (allow 1 KES rounding tolerance)
        double splitTotal = request.contributors().stream()
                .mapToDouble(SplitPaymentRequest.ContributorRequest::amount).sum();
        if (Math.abs(splitTotal - totalCharge) > 1.0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    String.format("Split amounts total KES %.2f but booking total is KES %.2f",
                            splitTotal, totalCharge));
        }

        int expiryMinutes = request.expiryMinutes() != null && request.expiryMinutes() > 0
                ? request.expiryMinutes() : DEFAULT_EXPIRY_MINUTES;

        SplitPaymentGroup group = new SplitPaymentGroup();
        group.setBooking(booking);
        group.setTotalAmount(totalCharge);
        group.setPlatformFee(platformFee);
        group.setProcessingFee(processingFee);
        group.setDeliveryFee(deliveryFee);
        group.setDriverCharge(driverCharge);
        group.setOwnerPayout(ownerPayout);
        group.setStatus(SplitPaymentGroupStatus.PENDING);
        group.setInitiatedBy(authentication.getName());
        group.setExpiresAt(ZonedDateTime.now().plusMinutes(expiryMinutes));
        SplitPaymentGroup savedGroup = groupRepository.save(group);

        // Create a contribution + trigger STK Push for each payer
        for (SplitPaymentRequest.ContributorRequest c : request.contributors()) {
            SplitPaymentContribution contribution = new SplitPaymentContribution();
            contribution.setSplitGroup(savedGroup);
            contribution.setPayerPhone(c.payerPhone());
            contribution.setPayerName(c.payerName());
            contribution.setPayerEmail(c.payerEmail());
            contribution.setAmount(c.amount());
            contribution.setPaymentMethod("MPESA");

            // Link to registered user if phone matches
            if (c.payerEmail() != null && !c.payerEmail().isBlank()) {
                userRepository.findByEmailAddressIgnoreCase(c.payerEmail())
                        .ifPresent(contribution::setPayerUser);
            }

            SplitPaymentContribution saved = contributionRepository.save(contribution);

            try {
                DarajaClient.StkPushResult stk = darajaClient.stkPush(
                        c.payerPhone(), c.amount(),
                        bookingId.toString(),
                        "Split payment for booking " + bookingId);
                saved.setProviderOrderTrackingId(stk.checkoutRequestId());
                saved.setProviderMerchantRequestId(stk.merchantRequestId());
                saved.setStatus(SplitContributionStatus.PROCESSING);
                contributionRepository.save(saved);
                log.info("STK Push sent to {} for split contribution {} — amount={}",
                        c.payerPhone(), saved.getId(), c.amount());
            } catch (Exception e) {
                // Leave as PENDING so it can be retried; don't fail the whole group
                log.warn("STK Push failed for contributor {} on booking {} — {}",
                        c.payerPhone(), bookingId, e.getMessage());
            }
        }

        List<SplitPaymentContribution> contributions = contributionRepository
                .findBySplitGroupId(savedGroup.getId());
        return SplitPaymentResponse.from(savedGroup, contributions);
    }

    @Transactional(readOnly = true)
    public SplitPaymentResponse getStatus(UUID bookingId) {
        SplitPaymentGroup group = groupRepository
                .findByBookingIdAndStatusIn(bookingId,
                        List.of(SplitPaymentGroupStatus.PENDING,
                                SplitPaymentGroupStatus.PARTIAL,
                                SplitPaymentGroupStatus.COMPLETE))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active split payment found for this booking"));
        List<SplitPaymentContribution> contributions = contributionRepository
                .findBySplitGroupId(group.getId());
        return SplitPaymentResponse.from(group, contributions);
    }

    /** Re-trigger STK Push for a failed or pending contribution. */
    @Transactional
    public SplitPaymentResponse retryContribution(UUID bookingId, UUID contributionId,
                                                   Authentication authentication) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the booking owner can retry");
        }

        SplitPaymentContribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contribution not found"));

        if (!contribution.getSplitGroup().getBooking().getId().equals(bookingId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contribution does not belong to this booking");
        }
        if (contribution.getStatus() == SplitContributionStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This contribution is already paid");
        }

        DarajaClient.StkPushResult stk = darajaClient.stkPush(
                contribution.getPayerPhone(), contribution.getAmount(),
                bookingId.toString(),
                "Split payment for booking " + bookingId);
        contribution.setProviderOrderTrackingId(stk.checkoutRequestId());
        contribution.setProviderMerchantRequestId(stk.merchantRequestId());
        contribution.setStatus(SplitContributionStatus.PROCESSING);
        contributionRepository.save(contribution);

        SplitPaymentGroup group = contribution.getSplitGroup();
        List<SplitPaymentContribution> all = contributionRepository.findBySplitGroupId(group.getId());
        return SplitPaymentResponse.from(group, all);
    }

    /**
     * Called from MpesaController.stkCallback() when a CheckoutRequestID
     * belongs to a split contribution rather than a regular payment.
     */
    @Transactional
    public boolean handleStkCallback(String checkoutRequestId, int resultCode, String receipt) {
        SplitPaymentContribution contribution = contributionRepository
                .findByProviderOrderTrackingId(checkoutRequestId).orElse(null);
        if (contribution == null) return false;

        if (resultCode != 0) {
            contribution.setStatus(SplitContributionStatus.FAILED);
            contributionRepository.save(contribution);
            log.warn("Split contribution {} FAILED — ResultCode={}", contribution.getId(), resultCode);
            return true;
        }

        contribution.setStatus(SplitContributionStatus.PAID);
        contribution.setProviderReceipt(receipt);
        contributionRepository.save(contribution);
        log.info("Split contribution {} PAID — receipt={}", contribution.getId(), receipt);

        checkAndFinalize(contribution.getSplitGroup());
        return true;
    }

    /** If all contributions are PAID, create the master Payment and confirm the booking. */
    private void checkAndFinalize(SplitPaymentGroup group) {
        List<SplitPaymentContribution> all = contributionRepository.findBySplitGroupId(group.getId());
        boolean allPaid = all.stream()
                .allMatch(c -> c.getStatus() == SplitContributionStatus.PAID);
        boolean anyPaid = all.stream()
                .anyMatch(c -> c.getStatus() == SplitContributionStatus.PAID);

        if (allPaid) {
            group.setStatus(SplitPaymentGroupStatus.COMPLETE);

            Payment payment = new Payment();
            payment.setBooking(group.getBooking());
            payment.setChargeType(PaymentChargeType.RENTAL);
            payment.setProvider("MPESA_SPLIT");
            payment.setPaymentMethod("MPESA_SPLIT");
            payment.setAmount(group.getTotalAmount());
            payment.setPlatformFee(group.getPlatformFee());
            payment.setTransactionFee(group.getProcessingFee());
            payment.setDeliveryFee(group.getDeliveryFee());
            payment.setDriverCharge(group.getDriverCharge());
            payment.setOwnerPayout(group.getOwnerPayout());
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            // Concatenate receipts from all contributions
            String receipts = all.stream()
                    .filter(c -> c.getProviderReceipt() != null)
                    .map(SplitPaymentContribution::getProviderReceipt)
                    .reduce((a, b) -> a + "," + b).orElse(null);
            payment.setProviderReceipt(receipts);
            Payment savedPayment = paymentRepository.save(payment);

            group.setPayment(savedPayment);
            groupRepository.save(group);

            log.info("Split payment complete for booking {} — master payment {}",
                    group.getBooking().getId(), savedPayment.getId());

            try { receiptService.generate(savedPayment); } catch (Exception e) {
                log.warn("Receipt generation failed for split payment {}", savedPayment.getId(), e);
            }
            bookingService.notifyBookingConfirmed(group.getBooking());

        } else if (anyPaid) {
            group.setStatus(SplitPaymentGroupStatus.PARTIAL);
            groupRepository.save(group);

            // Notify booking owner about partial progress
            try {
                long paidCount = all.stream().filter(c -> c.getStatus() == SplitContributionStatus.PAID).count();
                notificationService.createNotification(
                        group.getBooking().getUser(),
                        "Split Payment Update",
                        String.format("%d of %d contributors have paid. Waiting for remaining payments.",
                                paidCount, all.size()),
                        "SPLIT_PAYMENT_PARTIAL",
                        group.getId(),
                        "PAYMENT"
                );
            } catch (Exception e) {
                log.warn("Failed to send partial split payment notification", e);
            }
        }
    }

    /** Expire groups that have passed their expiry time without completing. */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void expireStaleGroups() {
        List<SplitPaymentGroup> expired = groupRepository
                .findByStatusAndExpiresAtBefore(SplitPaymentGroupStatus.PENDING, ZonedDateTime.now());
        expired.addAll(groupRepository
                .findByStatusAndExpiresAtBefore(SplitPaymentGroupStatus.PARTIAL, ZonedDateTime.now()));
        for (SplitPaymentGroup group : expired) {
            group.setStatus(SplitPaymentGroupStatus.EXPIRED);
            groupRepository.save(group);
            log.info("Split payment group {} expired for booking {}", group.getId(), group.getBooking().getId());
            try {
                notificationService.createNotification(
                        group.getBooking().getUser(),
                        "Split Payment Expired",
                        "Your split payment session expired before all contributors paid. Please initiate a new payment.",
                        "SPLIT_PAYMENT_EXPIRED",
                        group.getId(),
                        "PAYMENT"
                );
            } catch (Exception e) {
                log.warn("Failed to send expiry notification for split group {}", group.getId(), e);
            }
        }
    }
}
