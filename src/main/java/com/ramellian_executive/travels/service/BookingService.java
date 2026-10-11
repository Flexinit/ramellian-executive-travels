package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;

import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.BookingGroupRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserDocumentRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.service.vehicle.VehicleReviewService;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BookingService {
    private static final List<BookingStatus> RESERVING_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE);
    private final BookingRepository bookingRepository;
    private final BookingGroupRepository bookingGroupRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final UserDocumentRepository documentRepository;
    private final PaymentRepository paymentRepository;
    private final VerificationNotificationService notificationService;
    private final NotificationService appNotificationService;
    private final PlatformSettingsService platformSettingsService;
    private final VehicleReviewService reviewService;
    @Value("${app.booking.pending-expiration-ms:900000}")
    private long pendingExpirationMs;

    @org.springframework.beans.factory.annotation.Autowired
    public BookingService(BookingRepository bookingRepository, BookingGroupRepository bookingGroupRepository,
                          VehicleRepository vehicleRepository,
                          UserRepository userRepository, UserDocumentRepository documentRepository,
                          PaymentRepository paymentRepository,
                          VerificationNotificationService notificationService,
                          NotificationService appNotificationService,
                          PlatformSettingsService platformSettingsService,
                          VehicleReviewService reviewService) {
        this.bookingRepository = bookingRepository;
        this.bookingGroupRepository = bookingGroupRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.appNotificationService = appNotificationService;
        this.platformSettingsService = platformSettingsService;
        this.reviewService = reviewService;
    }

    @Transactional
    public BookingResponse create(BookingRequest request, Authentication authentication) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
        BookingMode mode;
        try {
            mode = BookingMode.valueOf(request.bookingMode().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking mode must be SELF_DRIVE or CHAUFFEURED");
        }
        User renter;
        if (request.renterId() != null) {
            if (!has(authentication, "BOOKING_MANAGE")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only staff with BOOKING_MANAGE permission can create a booking on behalf of another user");
            }
            renter = userRepository.findById(request.renterId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Renter not found"));
        } else {
            renter = findUser(authentication.getName());
        }
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!vehicle.isAvailable() || vehicle.getApprovalStatus() != VehicleApprovalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle is not available or has not been approved");
        }
        if (bookingRepository.existsOverlappingBooking(vehicle.getId(), request.startTime(),
                request.endTime(), RESERVING_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle is already booked for this period");
        }
        Booking booking = new Booking();
        booking.setVehicle(vehicle);
        if (mode == BookingMode.SELF_DRIVE && !vehicle.isSelfDrive()
                || mode == BookingMode.CHAUFFEURED && !vehicle.isChauffeured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle does not support the selected booking mode");
        }
        booking.setUser(renter);
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingMode(mode);

        if (request.tripType() != null) {
            TripType tripType = request.tripType();
            if (!vehicle.getTripTypes().isEmpty() 
                    && !vehicle.getTripTypes().contains(TripType.GENERAL) 
                    && !vehicle.getTripTypes().contains(tripType)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, 
                        "Vehicle does not support the selected trip type");
            }
            booking.setTripType(tripType);
        }

        if (request.renterNotes() != null && !request.renterNotes().isBlank()) {
            booking.setRenterNotes(request.renterNotes().trim());
        }
        if (request.pickupLocation() != null && !request.pickupLocation().isBlank()) {
            booking.setPickupLocation(request.pickupLocation().trim());
        }
        if (request.dropoffLocation() != null && !request.dropoffLocation().isBlank()) {
            booking.setDropoffLocation(request.dropoffLocation().trim());
        }
        booking.setDeliveryRequested(Boolean.TRUE.equals(request.deliveryRequested()));
        booking.setRenterContactEmail(renter.getEmailAddress());
        booking.setRenterContactPhone(renter.getPhoneNumber());

        Booking savedBooking = bookingRepository.save(booking);
        if (appNotificationService != null) {
            String vehicleTitle = (vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                    + (vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                    ? " (" + vehicle.getRegistrationNumber() + ")" : "");
            appNotificationService.createNotification(
                    renter,
                    "Booking Created for " + vehicleTitle,
                    "Your booking for " + vehicleTitle + " has been placed successfully. Please complete payment to confirm.",
                    "BOOKING_CREATED",
                    savedBooking.getId(),
                    "BOOKING"
            );
            String renterContact = buildContactSummary(renter);
            String adminMessage = "New booking for " + vehicleTitle + " by " + renterContact
                    + ". Booking ID: " + savedBooking.getId();
            for (User admin : userRepository.findByRoles_RoleName("SUPER_ADMIN")) {
                appNotificationService.createNotification(admin, "New Booking — " + vehicleTitle,
                        adminMessage, "BOOKING_CREATED", savedBooking.getId(), "BOOKING");
            }
            for (User admin : userRepository.findByRoles_RoleName("ADMIN")) {
                appNotificationService.createNotification(admin, "New Booking — " + vehicleTitle,
                        adminMessage, "BOOKING_CREATED", savedBooking.getId(), "BOOKING");
            }
            User owner = vehicle.getOwner();
            if (owner != null && !owner.getId().equals(renter.getId())) {
                appNotificationService.createNotification(owner, "New Booking on Your Vehicle",
                        "A new booking has been placed for " + vehicleTitle + ". Please await confirmation.",
                        "BOOKING_CREATED", savedBooking.getId(), "BOOKING");
            }
        }
        List<String> warnings = documentWarnings(renter, mode);
        BookingResponse response = BookingResponse.from(savedBooking,
                platformSettingsService.getCommissionRatePct(),
                platformSettingsService.getPesapalTransactionFeeRatePct(),
                platformSettingsService.getDeliveryFlatFee(),
                platformSettingsService.getDriverFlatCharge()).withoutOwnerPayout();
        return warnings.isEmpty() ? response : response.withWarnings(warnings);
    }

    private List<String> documentWarnings(User renter, BookingMode mode) {
        List<String> warnings = new ArrayList<>();
        boolean hasId = !blank(renter.getIdNumber())
                && (documentRepository.existsByUserIdAndDocumentTypeAndReviewStatus(
                        renter.getId(), DocumentType.NATIONAL_ID, DocumentReviewStatus.APPROVED)
                    || documentRepository.existsByUserIdAndDocumentTypeAndReviewStatus(
                        renter.getId(), DocumentType.PASSPORT, DocumentReviewStatus.APPROVED));
        if (!hasId) {
            warnings.add("Please upload and get your national ID or passport approved before pickup. " +
                         "Your booking may be cancelled without a refund if documents are missing at handover.");
        }
        if (mode == BookingMode.SELF_DRIVE
                && !documentRepository.existsByUserIdAndDocumentTypeAndReviewStatus(
                        renter.getId(), DocumentType.DRIVING_LICENSE, DocumentReviewStatus.APPROVED)) {
            warnings.add("A valid driving license is required for self-drive rentals. " +
                         "Please upload and get it approved before pickup or your booking may be cancelled without a refund.");
        }
        return warnings;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(Authentication authentication) {
        double rate = platformSettingsService.getCommissionRatePct();
        return bookingRepository.findByUserEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName())
                .stream().map(b -> BookingResponse.from(b, rate).withoutOwnerPayout()).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> forOwner(Authentication authentication) {
        double rate = platformSettingsService.getCommissionRatePct();
        return bookingRepository.findByVehicleOwnerEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName())
                .stream().map(b -> BookingResponse.from(b, rate)).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id, Authentication authentication) {
        Booking booking = find(id);
        boolean renter = booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean owner = booking.getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean manager = has(authentication, "BOOKING_MANAGE") || has(authentication, "ROLE_SUPER_ADMIN") || has(authentication, "ROLE_ADMIN");
        if (!renter && !owner && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this booking");
        }
        BookingResponse response = BookingResponse.from(booking, platformSettingsService.getCommissionRatePct());
        return (owner || manager) ? response : response.withoutOwnerPayout();
    }

    @Transactional
    public BookingResponse assignChauffeur(UUID id, UUID chauffeurId, Authentication authentication) {
            Booking booking = find(id);
            boolean owner = booking.getVehicle().getOwner().getEmailAddress()
                    .equalsIgnoreCase(authentication.getName());
            if (!owner && !has(authentication, "BOOKING_MANAGE")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the vehicle owner or booking manager can assign a chauffeur");
            }
            if (booking.getBookingMode() != BookingMode.CHAUFFEURED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only chauffeured bookings can have a chauffeur assigned");
            }
            User chauffeur = userRepository.findById(chauffeurId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Chauffeur not found"));
            if (chauffeur.getRoles().stream().noneMatch(role ->
                    UserRole.CHAUFFEUR.equals(role.getRoleName()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Selected user is not a chauffeur");
            }
            if (!chauffeur.isEnabled()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Selected chauffeur account is disabled");
            }
            if (bookingRepository.existsOverlappingChauffeurBooking(chauffeurId, id,
                    booking.getStartTime(), booking.getEndTime(), RESERVING_STATUSES)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chauffeur is unavailable for this period");
            }
            booking.setChauffeur(chauffeur);
            return BookingResponse.from(bookingRepository.save(booking), platformSettingsService.getCommissionRatePct());
        }

    @Transactional
    @Scheduled(fixedDelay = 60000)
    public void transitionBookingStatuses() {
        ZonedDateTime now = ZonedDateTime.now();
        bookingRepository.findByStatusAndStartTimeBefore(BookingStatus.CONFIRMED, now)
                .forEach(booking -> {
                    booking.setStatus(BookingStatus.ACTIVE);
                    bookingRepository.save(booking);
                });
        bookingRepository.findByStatusAndEndTimeBefore(BookingStatus.ACTIVE, now)
                .forEach(booking -> {
                    booking.setStatus(BookingStatus.COMPLETED);
                    bookingRepository.save(booking);
                });
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.booking.pending-expiration-ms:900000}")
    public void expireUnpaidPendingBookings() {
            ZonedDateTime cutoff = ZonedDateTime.now().minusNanos(
                    java.time.Duration.ofMillis(pendingExpirationMs).toNanos());
            bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)
                    .forEach(booking -> {
                        if (paymentRepository == null
                                || (!paymentRepository.existsByBookingIdAndStatus(
                                        booking.getId(), PaymentStatus.SUCCESSFUL)
                                    && !paymentRepository.existsByBookingIdAndStatusAndProviderOrderTrackingIdIsNotNull(
                                        booking.getId(), PaymentStatus.PENDING))) {
                            booking.setStatus(BookingStatus.CANCELLED);
                            booking.setDispatcherNotes("Automatically expired before payment");
                            bookingRepository.save(booking);
                        }
                    });
    }

    @Transactional(readOnly = true)
    public BookingReportResponse report(List<Booking> bookings, boolean includeOwnerPayout) {
        ZonedDateTime now = ZonedDateTime.now();
        List<Booking> upcoming = bookings.stream()
                .filter(b -> (b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED)
                        && b.getEndTime().isAfter(now)).toList();
        List<Booking> active = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.ACTIVE
                        || (b.getStatus() == BookingStatus.CONFIRMED
                        && !b.getStartTime().isAfter(now) && b.getEndTime().isAfter(now))).toList();
        List<Booking> completed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).toList();
        List<Booking> cancelled = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).toList();
        List<Booking> rejected = bookings.stream().filter(b -> b.getStatus() == BookingStatus.REJECTED).toList();
        List<Payment> payments = paymentRepository == null ? List.of()
                : paymentRepository.findAllByBookingInAndStatus(bookings, PaymentStatus.SUCCESSFUL);
        double gross = payments.stream().mapToDouble(Payment::getAmount).sum();
        double platform = payments.stream().mapToDouble(Payment::getPlatformFee).sum();
        double payout = payments.stream().mapToDouble(Payment::getOwnerPayout).sum();
        // keyed by booking id so toResponses can substitute actual payment values
        java.util.Map<UUID, Payment> paymentByBooking = payments.stream()
                .collect(java.util.stream.Collectors.toMap(
                        p -> p.getBooking().getId(), p -> p, (a, b) -> a));
        return new BookingReportResponse(now, bookings.size(), upcoming.size(), active.size(),
                completed.size(), cancelled.size(), rejected.size(), gross, platform, payout,
                toResponses(upcoming, includeOwnerPayout, paymentByBooking),
                toResponses(active, includeOwnerPayout, paymentByBooking),
                toResponses(completed, includeOwnerPayout, paymentByBooking),
                toResponses(cancelled, includeOwnerPayout, paymentByBooking),
                toResponses(rejected, includeOwnerPayout, paymentByBooking));
    }

    public BookingReportResponse renterReport(Authentication authentication) {
        return report(bookingRepository.findByUserEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName()), false);
    }

    public BookingReportResponse ownerReport(Authentication authentication) {
        return report(bookingRepository.findByVehicleOwnerEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName()), true);
    }

    public BookingReportResponse platformReport() {
        return report(bookingRepository.findAllByOrderByStartTimeDesc(), true);
    }

    private List<BookingResponse> toResponses(List<Booking> bookings, boolean includeOwnerPayout,
                                              java.util.Map<UUID, Payment> paymentByBooking) {
        double rate = platformSettingsService.getCommissionRatePct();
        return bookings.stream().map(b -> {
            BookingResponse r = BookingResponse.from(b, rate);
            Payment p = paymentByBooking.get(b.getId());
            if (p != null) {
                r = r.withActualPaymentValues(
                        p.getAmount(), p.getPlatformFee(), p.getOwnerPayout());
            }
            return includeOwnerPayout ? r : r.withoutOwnerPayout();
        }).toList();
    }

    @Transactional
    public BookingResponse changeStatus(UUID id, BookingStatusRequest request,
                                        Authentication authentication) {
        Booking booking = find(id);
        BookingStatus next;
        try {
            next = BookingStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking status");
        }
        boolean manager = has(authentication, "BOOKING_MANAGE");
        boolean owner = booking.getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean renter = booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        if (next == BookingStatus.CANCELLED && !renter && !owner && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this booking");
        }
        if (next != BookingStatus.CANCELLED && !owner && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the vehicle owner or booking manager can change this status");
        }
        if (!validTransition(booking.getStatus(), next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid booking status transition");
        }
        if (next == BookingStatus.CONFIRMED && paymentRepository != null
                && !paymentRepository.existsByBookingIdAndStatus(id, PaymentStatus.SUCCESSFUL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking can only be confirmed after successful payment");
        }
        booking.setStatus(next);
        booking.setDispatcherNotes(request.notes());
        Booking savedBooking = bookingRepository.save(booking);
        Vehicle veh = savedBooking.getVehicle();
        String vehicleTitle = veh != null && veh.getModel() != null ? veh.getModel() : "Vehicle";
        if (appNotificationService != null && savedBooking.getUser() != null) {
            appNotificationService.createNotification(
                    savedBooking.getUser(),
                    "Booking " + next.name() + " for " + vehicleTitle,
                    "Your booking for " + vehicleTitle + " is now " + next.name().toLowerCase() + ".",
                    "BOOKING_STATUS_CHANGED",
                    savedBooking.getId(),
                    "BOOKING"
            );
        }
        if (next == BookingStatus.CONFIRMED) {
            List<String> warnings = documentWarnings(savedBooking.getUser(), savedBooking.getBookingMode());
            notificationService.sendBookingCreatedToRenter(savedBooking, warnings);
            if (veh != null && veh.getOwner() != null
                    && !veh.getOwner().getId().equals(savedBooking.getUser().getId())) {
                notificationService.sendBookingNotification(savedBooking, platformSettingsService.getCommissionRatePct());
            }
            for (User admin : userRepository.findByRoles_RoleName("SUPER_ADMIN")) {
                notificationService.sendBookingCreatedToAdmin(savedBooking, admin);
            }
            for (User admin : userRepository.findByRoles_RoleName("ADMIN")) {
                notificationService.sendBookingCreatedToAdmin(savedBooking, admin);
            }
        }
        return BookingResponse.from(savedBooking, platformSettingsService.getCommissionRatePct());
    }

    @Transactional
    public BookingGroupResponse createBatch(BatchBookingRequest request, Authentication authentication) {
        User renter = findUser(authentication.getName());
        BookingGroup group = new BookingGroup();
        group.setRenter(renter);
        group.setStatus(BookingGroupStatus.PENDING);
        BookingGroup savedGroup = bookingGroupRepository.save(group);

        List<BookingResponse> responses = new ArrayList<>();
        double total = 0.0;
        for (BookingRequest bookingRequest : request.bookings()) {
            BookingResponse resp = create(bookingRequest, authentication);
            // set the group on the saved booking
            Booking booking = bookingRepository.findById(resp.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Booking not found after creation"));
            booking.setBookingGroup(savedGroup);
            bookingRepository.save(booking);
            responses.add(resp);
            if (resp.estimatedAmount() != null) {
                total += resp.estimatedAmount();
            }
        }
        return new BookingGroupResponse(savedGroup.getId(), savedGroup.getStatus().name(),
                responses, BigDecimal.valueOf(total));
    }

    @Transactional(readOnly = true)
    public BookingQuoteResponse quote(BookingQuoteRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        long totalHours = Duration.between(request.startTime(), request.endTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        BigDecimal base = BigDecimal.valueOf(vehicle.getPricePerDay()).multiply(BigDecimal.valueOf(days));
        BigDecimal commissionRate = BigDecimal.valueOf(platformSettingsService.getCommissionRatePct()).divide(BigDecimal.valueOf(100));
        BigDecimal fee = base.multiply(commissionRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal txFeeRate = BigDecimal.valueOf(platformSettingsService.getPesapalTransactionFeeRatePct()).divide(BigDecimal.valueOf(100));
        BigDecimal txFee = base.multiply(txFeeRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal deliveryFee = Boolean.TRUE.equals(request.deliveryRequested())
                ? BigDecimal.valueOf(platformSettingsService.getDeliveryFlatFee()).setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal driverCharge = com.ramellian_executive.travels.entity.BookingMode.CHAUFFEURED.equals(request.bookingMode())
                ? BigDecimal.valueOf(platformSettingsService.getDriverFlatCharge()).setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        return new BookingQuoteResponse(vehicle.getId(), days,
                BigDecimal.valueOf(vehicle.getPricePerDay()), base, fee, txFee, deliveryFee, driverCharge,
                base.add(txFee).add(deliveryFee).add(driverCharge));
    }

    @Transactional
    public BookingResponse recordReturn(UUID bookingId, BookingReturnRequest request, Authentication authentication) {
        Booking booking = find(bookingId);
        boolean isOwner = booking.getVehicle().getOwner().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        boolean isAdmin = has(authentication, "BOOKING_MANAGE");
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the vehicle owner or admin can record a return");
        }
        if (booking.getStatus() != BookingStatus.ACTIVE && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking must be ACTIVE or COMPLETED to record a return");
        }
        booking.setActualEndTime(request.actualEndTime());
        if (booking.getStatus() == BookingStatus.ACTIVE) {
            booking.setStatus(BookingStatus.COMPLETED);
        }
        if (request.actualEndTime().isAfter(booking.getEndTime()) && paymentRepository != null) {
            long overstayHours = java.time.Duration.between(booking.getEndTime(), request.actualEndTime()).toHours();
            long overstayDays = Math.max(1, (overstayHours + 23) / 24);
            double overstayAmount = booking.getVehicle().getPricePerDay() * overstayDays;
            boolean alreadyCharged = paymentRepository
                    .findByBookingIdAndChargeType(bookingId,
                            com.ramellian_executive.travels.entity.PaymentChargeType.OVERSTAY)
                    .isPresent();
            if (!alreadyCharged) {
                Payment overstayPayment = new Payment();
                overstayPayment.setBooking(booking);
                overstayPayment.setChargeType(com.ramellian_executive.travels.entity.PaymentChargeType.OVERSTAY);
                overstayPayment.setPaymentMethod("PESAPAL");
                overstayPayment.setProvider("PESAPAL");
                overstayPayment.setAmount(overstayAmount);
                overstayPayment.setPlatformFee(overstayAmount * 0.01);
                overstayPayment.setOwnerPayout(overstayAmount - overstayAmount * 0.01);
                overstayPayment.setStatus(PaymentStatus.PENDING);
                paymentRepository.save(overstayPayment);
            }
        }
        Booking saved = bookingRepository.save(booking);
        if (request.review() != null) {
            reviewService.create(saved, request.review(), authentication);
        }
        return BookingResponse.from(saved);
    }

    private boolean validTransition(BookingStatus from, BookingStatus to) {
        return switch (from) {
            case PENDING -> to == BookingStatus.CONFIRMED || to == BookingStatus.REJECTED || to == BookingStatus.CANCELLED;
            case CONFIRMED -> to == BookingStatus.ACTIVE || to == BookingStatus.CANCELLED;
            case ACTIVE -> to == BookingStatus.COMPLETED || to == BookingStatus.CANCELLED;
            default -> false;
        };
    }

    private Booking find(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmailAddressIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static String buildContactSummary(User user) {
        String name = java.util.stream.Stream.of(user.getFirstName(), user.getLastName())
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        String contact = java.util.stream.Stream.of(user.getEmailAddress(), user.getPhoneNumber())
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" / "));
        return (name.isBlank() ? "" : name + " — ") + contact;
    }
}
