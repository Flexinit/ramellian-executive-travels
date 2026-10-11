package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.*;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BookingPolicyService {

    private static final List<BookingStatus> RESERVING_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE);

    private final BookingPolicyRepository policyRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final PlatformSettingsService platformSettingsService;

    public BookingPolicyService(BookingPolicyRepository policyRepository,
                                BookingRepository bookingRepository,
                                PaymentRepository paymentRepository,
                                UserRepository userRepository,
                                VehicleRepository vehicleRepository,
                                PlatformSettingsService platformSettingsService) {
        this.policyRepository = policyRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.platformSettingsService = platformSettingsService;
    }

    // ── Policy resolution ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BookingPolicy resolveFor(Booking booking) {
        UUID ownerId = booking.getVehicle().getOwner().getId();
        return policyRepository.findFirstByOwnerIdOrderByCreatedAtDesc(ownerId)
                .orElseGet(() -> policyRepository.findFirstByOwnerIsNullOrderByCreatedAtDesc()
                        .orElseGet(BookingPolicy::new)); // built-in defaults via field initializers
    }

    // ── Renter: cancel ───────────────────────────────────────────────────────

    @Transactional
    public CancellationResponse cancel(UUID bookingId, Authentication authentication) {
        Booking booking = findBooking(bookingId);
        requireRenter(booking, authentication);

        if (booking.getStatus() == BookingStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An active rental cannot be cancelled; please complete the drop-off process");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking cannot be cancelled in its current state");
        }

        double paidAmount = 0.0;
        int refundPct = 100;
        String message;

        // PENDING bookings have not been paid — always free
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            BookingPolicy policy = resolveFor(booking);
            long hoursToStart = Duration.between(ZonedDateTime.now(), booking.getStartTime()).toHours();

            if (hoursToStart < policy.getCancellationDeadlineHours()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cancellations are not allowed within " + policy.getCancellationDeadlineHours()
                                + " hour(s) of the booking start time");
            }

            paidAmount = paymentRepository.findByBookingId(bookingId)
                    .map(p -> p.getAmount() != null ? p.getAmount() : 0.0)
                    .orElse(0.0);

            if (hoursToStart >= policy.getFreeCancellationWindowHours()) {
                refundPct = 100;
                message = "Full refund of " + formatAmount(paidAmount) + " will be processed.";
            } else {
                refundPct = policy.getLateCancellationRefundPct();
                message = refundPct + "% refund (" + formatAmount(paidAmount * refundPct / 100.0)
                        + ") applies — cancellation made within the late window.";
            }
        } else {
            message = "Booking cancelled before payment; no charge applies.";
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        return new CancellationResponse(
                booking.getId(),
                BookingStatus.CANCELLED.name(),
                paidAmount,
                paidAmount * refundPct / 100.0,
                refundPct,
                message
        );
    }

    // ── Renter: postpone ─────────────────────────────────────────────────────

    @Transactional
    public BookingResponse postpone(UUID bookingId, PostponementRequest request,
                                    Authentication authentication) {
        Booking booking = findBooking(bookingId);
        requireRenter(booking, authentication);

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only pending or confirmed bookings can be postponed");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "End time must be after start time");
        }

        BookingPolicy policy = resolveFor(booking);

        if (!policy.isPostponementAllowed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Postponements are not permitted for this booking");
        }
        if (booking.getPostponementCount() >= policy.getMaxPostponements()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Maximum postponements (" + policy.getMaxPostponements() + ") already used");
        }

        long hoursToStart = Duration.between(ZonedDateTime.now(), booking.getStartTime()).toHours();
        if (hoursToStart < policy.getPostponementDeadlineHours()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Postponements must be requested at least " + policy.getPostponementDeadlineHours()
                            + " hour(s) before the current start time");
        }

        if (bookingRepository.existsOverlappingBookingExcluding(
                booking.getVehicle().getId(), request.startTime(), request.endTime(),
                RESERVING_STATUSES, bookingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicle is already booked for the requested time");
        }

        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        booking.setPostponementCount(booking.getPostponementCount() + 1);
        return BookingResponse.from(bookingRepository.save(booking), platformSettingsService.getCommissionRatePct());
    }

    // ── Policy CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public BookingPolicyResponse createOrUpdatePlatformPolicy(BookingPolicyRequest request,
                                                              Authentication authentication) {
        BookingPolicy policy = policyRepository.findFirstByOwnerIsNullOrderByCreatedAtDesc()
                .orElseGet(BookingPolicy::new);
        apply(policy, request);
        policy.setOwner(null);
        return BookingPolicyResponse.from(policyRepository.save(policy));
    }

    @Transactional
    public BookingPolicyResponse createOrUpdateOwnerPolicy(BookingPolicyRequest request,
                                                           Authentication authentication) {
        User owner = userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        BookingPolicy policy = policyRepository.findFirstByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .orElseGet(BookingPolicy::new);
        apply(policy, request);
        policy.setOwner(owner);
        return BookingPolicyResponse.from(policyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public BookingPolicyResponse getPlatformPolicy() {
        return policyRepository.findFirstByOwnerIsNullOrderByCreatedAtDesc()
                .map(BookingPolicyResponse::from)
                .orElseGet(BookingPolicyResponse::defaults);
    }

    @Transactional(readOnly = true)
    public BookingPolicyResponse getOwnerPolicy(Authentication authentication) {
        User owner = userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return policyRepository.findFirstByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .map(BookingPolicyResponse::from)
                .orElseGet(BookingPolicyResponse::defaults);
    }

    @Transactional(readOnly = true)
    public BookingPolicyResponse getEffectivePolicyForBooking(UUID bookingId,
                                                               Authentication authentication) {
        Booking booking = findBooking(bookingId);
        boolean renter = booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean owner = booking.getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        if (!renter && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return BookingPolicyResponse.from(resolveFor(booking));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void apply(BookingPolicy policy, BookingPolicyRequest req) {
        policy.setFreeCancellationWindowHours(req.freeCancellationWindowHours());
        policy.setCancellationDeadlineHours(req.cancellationDeadlineHours());
        policy.setLateCancellationRefundPct(req.lateCancellationRefundPct());
        policy.setPostponementAllowed(req.postponementAllowed());
        policy.setPostponementDeadlineHours(req.postponementDeadlineHours());
        policy.setMaxPostponements(req.maxPostponements());
    }

    private Booking findBooking(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void requireRenter(Booking booking, Authentication authentication) {
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the renter can perform this action");
        }
    }

    private String formatAmount(double amount) {
        return String.format("%.2f", amount);
    }
}
