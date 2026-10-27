package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;

import com.ramellian_executive.travels.repository.BookingContractRepository;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.BookingGroupRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserDocumentRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.service.vehicle.VehicleReviewService;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import com.ramellian_executive.travels.service.EmailTemplateService;
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
    private final BookingContractRepository contractRepository;
    private final VerificationNotificationService notificationService;
    private final NotificationService appNotificationService;
    private final PlatformSettingsService platformSettingsService;
    private final VehicleReviewService reviewService;

    @org.springframework.beans.factory.annotation.Autowired
    public BookingService(BookingRepository bookingRepository, BookingGroupRepository bookingGroupRepository,
                          VehicleRepository vehicleRepository,
                          UserRepository userRepository, UserDocumentRepository documentRepository,
                          PaymentRepository paymentRepository,
                          BookingContractRepository contractRepository,
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
        this.contractRepository = contractRepository;
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
        if (request.passengerCount() != null && request.passengerCount() > 0) {
            booking.setPassengerCount(request.passengerCount());
        }
        booking.setRenterContactEmail(renter.getEmailAddress());
        booking.setRenterContactPhone(renter.getPhoneNumber());

        Booking savedBooking = bookingRepository.save(booking);
        List<String> warnings = documentWarnings(renter, mode);
        BookingResponse response = BookingResponse.from(savedBooking,
                commissionRateFor(savedBooking),
                platformSettingsService.getPesapalTransactionFeeRatePct(),
                platformSettingsService.getDeliveryFlatFee(),
                platformSettingsService.getDriverFlatCharge()).withoutOwnerPayout();
        return warnings.isEmpty() ? response : response.withWarnings(warnings);
    }

    @Transactional
    public void notifyBookingConfirmed(Booking booking) {
        User renter = booking.getUser();
        Vehicle vehicle = booking.getVehicle();
        String vehicleTitle = (vehicle != null && vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                + (vehicle != null && vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                ? " (" + vehicle.getRegistrationNumber() + ")" : "");
        if (appNotificationService != null && renter != null) {
            appNotificationService.createNotification(renter,
                    "Booking Confirmed — " + vehicleTitle,
                    "Your booking for " + vehicleTitle + " is confirmed. Payment received.",
                    "BOOKING_CONFIRMED", booking.getId(), "BOOKING");
            User owner = vehicle != null ? vehicle.getOwner() : null;
            if (owner != null && !owner.getId().equals(renter.getId())) {
                String renterName = renter.getFirstName() != null ? renter.getFirstName() : "A customer";
                appNotificationService.createNotification(owner,
                        "New Confirmed Booking — " + vehicleTitle,
                        renterName + " has paid and confirmed a booking for " + vehicleTitle + ".",
                        "BOOKING_CONFIRMED", booking.getId(), "BOOKING");
            }
            for (User admin : userRepository.findByRoles_RoleName("SUPER_ADMIN")) {
                appNotificationService.createNotification(admin,
                        "Booking Confirmed — " + vehicleTitle,
                        "A booking for " + vehicleTitle + " has been confirmed after payment.",
                        "BOOKING_CONFIRMED", booking.getId(), "BOOKING");
            }
            for (User admin : userRepository.findByRoles_RoleName("ADMIN")) {
                appNotificationService.createNotification(admin,
                        "Booking Confirmed — " + vehicleTitle,
                        "A booking for " + vehicleTitle + " has been confirmed after payment.",
                        "BOOKING_CONFIRMED", booking.getId(), "BOOKING");
            }
        }
        if (renter != null) {
            notificationService.sendBookingCreatedToRenter(booking, documentWarnings(renter, booking.getBookingMode()));
        }
        if (vehicle != null && vehicle.getOwner() != null
                && (renter == null || !vehicle.getOwner().getId().equals(renter.getId()))) {
            notificationService.sendBookingNotification(booking, commissionRateFor(booking));
        }
        for (User admin : userRepository.findByRoles_RoleName("SUPER_ADMIN")) {
            notificationService.sendBookingCreatedToAdmin(booking, admin);
        }
        for (User admin : userRepository.findByRoles_RoleName("ADMIN")) {
            notificationService.sendBookingCreatedToAdmin(booking, admin);
        }
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
        if (mode == BookingMode.SELF_DRIVE
                && !documentRepository.existsByUserIdAndDocumentTypeAndReviewStatus(
                        renter.getId(), DocumentType.CERTIFICATE_OF_GOOD_CONDUCT, DocumentReviewStatus.APPROVED)) {
            warnings.add("A Certificate of Good Conduct is required for self-drive rentals. " +
                         "Please upload and get it approved before pickup or your booking may be cancelled without a refund.");
        }
        return warnings;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private double commissionRateFor(Booking booking) {
        if (booking.getStartTime() == null || booking.getEndTime() == null) {
            return platformSettingsService.getCommissionRatePct();
        }
        long hours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (hours + 23) / 24);
        return platformSettingsService.getCommissionRatePct(days);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(Authentication authentication) {
        return bookingRepository.findActiveByUserEmailIgnoreCase(authentication.getName())
                .stream().map(b -> BookingResponse.from(b, commissionRateFor(b)).withoutOwnerPayout()).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> forOwner(Authentication authentication) {
        return bookingRepository.findActiveByVehicleOwnerEmailIgnoreCase(authentication.getName())
                .stream().map(b -> BookingResponse.from(b, commissionRateFor(b))).toList();
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
        BookingResponse response = BookingResponse.from(booking, commissionRateFor(booking));
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
        bookingRepository.bulkTransitionByStartTime(BookingStatus.CONFIRMED, BookingStatus.ACTIVE, now);
        bookingRepository.bulkTransitionByEndTime(BookingStatus.ACTIVE, BookingStatus.COMPLETED, now);
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
        return report(bookingRepository.findActiveByUserEmailIgnoreCase(authentication.getName()), false);
    }

    public BookingReportResponse ownerReport(Authentication authentication) {
        return report(bookingRepository.findActiveByVehicleOwnerEmailIgnoreCase(authentication.getName()), true);
    }

    public BookingReportResponse platformReport() {
        return report(bookingRepository.findAllByOrderByStartTimeDesc(), true);
    }

    private List<BookingResponse> toResponses(List<Booking> bookings, boolean includeOwnerPayout,
                                              java.util.Map<UUID, Payment> paymentByBooking) {
        return bookings.stream().map(b -> {
            BookingResponse r = BookingResponse.from(b, commissionRateFor(b));
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
        if (next == BookingStatus.ARCHIVED && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a booking manager can archive a booking");
        }
        if (next == BookingStatus.CANCELLED && !renter && !owner && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this booking");
        }
        if (next != BookingStatus.CANCELLED && next != BookingStatus.ARCHIVED && !owner && !manager) {
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
                notificationService.sendBookingNotification(savedBooking, commissionRateFor(savedBooking));
            }
            for (User admin : userRepository.findByRoles_RoleName("SUPER_ADMIN")) {
                notificationService.sendBookingCreatedToAdmin(savedBooking, admin);
            }
            for (User admin : userRepository.findByRoles_RoleName("ADMIN")) {
                notificationService.sendBookingCreatedToAdmin(savedBooking, admin);
            }
        }
        return BookingResponse.from(savedBooking, commissionRateFor(savedBooking));
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
        BigDecimal commissionRate = BigDecimal.valueOf(platformSettingsService.getCommissionRatePct(days)).divide(BigDecimal.valueOf(100));
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

    @Transactional
    public BookingResponse changeVehicle(UUID bookingId, ChangeVehicleRequest request, Authentication authentication) {
        Booking booking = find(bookingId);
        boolean isRenter = booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean isOwner = booking.getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean isManager = has(authentication, "BOOKING_MANAGE");

        if (!isRenter && !isOwner && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the renter, vehicle owner, or a booking manager can change the vehicle");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicle can only be changed for PENDING or CONFIRMED bookings");
        }
        Vehicle newVehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!newVehicle.isAvailable() || newVehicle.getApprovalStatus() != VehicleApprovalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "New vehicle is not available or not approved");
        }
        if (bookingRepository.existsOverlappingBooking(newVehicle.getId(), booking.getStartTime(),
                booking.getEndTime(), RESERVING_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "New vehicle is already booked for this period");
        }
        BookingMode mode = booking.getBookingMode();
        if (mode == BookingMode.SELF_DRIVE && !newVehicle.isSelfDrive()
                || mode == BookingMode.CHAUFFEURED && !newVehicle.isChauffeured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "New vehicle does not support the current booking mode (" + mode + ")");
        }

        // Void existing contract if one exists
        contractRepository.findByBookingId(bookingId).ifPresent(contract -> {
            contract.setStatus(ContractStatus.VOID);
            contractRepository.save(contract);
        });

        Vehicle oldVehicle = booking.getVehicle();
        booking.setVehicle(newVehicle);
        if (request.reason() != null && !request.reason().isBlank()) {
            String existing = booking.getRenterNotes() != null ? booking.getRenterNotes() + "\n" : "";
            booking.setRenterNotes(existing + "Vehicle changed: " + request.reason().trim());
        }
        Booking saved = bookingRepository.save(booking);

        String oldTitle = EmailTemplateService.vehicleTitle(oldVehicle);
        String newTitle = EmailTemplateService.vehicleTitle(newVehicle);
        String changeNote = "Vehicle changed from " + oldTitle + " to " + newTitle
                + (request.reason() != null && !request.reason().isBlank() ? ". Reason: " + request.reason() : "");

        if (appNotificationService != null) {
            appNotificationService.createNotification(saved.getUser(),
                    "Vehicle Updated — " + newTitle,
                    changeNote,
                    "BOOKING_VEHICLE_CHANGED", saved.getId(), "BOOKING");
            User newOwner = newVehicle.getOwner();
            if (newOwner != null && !newOwner.getId().equals(saved.getUser().getId())) {
                appNotificationService.createNotification(newOwner,
                        "Booking Assigned to Your Vehicle — " + newTitle,
                        "A booking has been reassigned to your vehicle " + newTitle + ".",
                        "BOOKING_VEHICLE_CHANGED", saved.getId(), "BOOKING");
            }
        }
        return BookingResponse.from(saved, commissionRateFor(saved)).withoutOwnerPayout();
    }

    private boolean validTransition(BookingStatus from, BookingStatus to) {
        return switch (from) {
            case PENDING -> to == BookingStatus.CONFIRMED || to == BookingStatus.REJECTED || to == BookingStatus.CANCELLED;
            case CONFIRMED -> to == BookingStatus.ACTIVE || to == BookingStatus.CANCELLED;
            case ACTIVE -> to == BookingStatus.COMPLETED || to == BookingStatus.CANCELLED;
            case COMPLETED, CANCELLED, REJECTED -> to == BookingStatus.ARCHIVED;
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
