package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.BookingGroupRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.repository.UserDocumentRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
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
    @Value("${app.booking.pending-expiration-ms:900000}")
    private long pendingExpirationMs;

    public BookingService(BookingRepository bookingRepository, VehicleRepository vehicleRepository,
                          UserRepository userRepository, UserDocumentRepository documentRepository) {
        this(bookingRepository, null, vehicleRepository, userRepository, documentRepository, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BookingService(BookingRepository bookingRepository, BookingGroupRepository bookingGroupRepository,
                          VehicleRepository vehicleRepository,
                          UserRepository userRepository, UserDocumentRepository documentRepository,
                          PaymentRepository paymentRepository,
                          VerificationNotificationService notificationService,
                          NotificationService appNotificationService) {
        this.bookingRepository = bookingRepository;
        this.bookingGroupRepository = bookingGroupRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
        this.appNotificationService = appNotificationService;
    }

    public BookingService(BookingRepository bookingRepository, VehicleRepository vehicleRepository,
                          UserRepository userRepository, UserDocumentRepository documentRepository,
                          PaymentRepository paymentRepository,
                          VerificationNotificationService notificationService) {
        this(bookingRepository, null, vehicleRepository, userRepository, documentRepository, paymentRepository, notificationService, null);
    }

    public BookingService(BookingRepository bookingRepository, VehicleRepository vehicleRepository,
                          UserRepository userRepository, UserDocumentRepository documentRepository,
                          PaymentRepository paymentRepository) {
        this(bookingRepository, null, vehicleRepository, userRepository, documentRepository, paymentRepository, null, null);
    }

    public BookingService(BookingRepository bookingRepository, VehicleRepository vehicleRepository,
                          UserRepository userRepository) {
        this(bookingRepository, null, vehicleRepository, userRepository, null, null, null, null);
    }

    @Transactional
    public BookingResponse create(BookingRequest request, Authentication authentication) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
        BookingMode mode;
        try {
            mode = BookingMode.valueOf(request.bookingMode().trim().toUpperCase(Locale.ROOT));
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
        if (documentRepository != null) {
            requireDocuments(renter, mode);
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

        Booking savedBooking = bookingRepository.save(booking);
        if (notificationService != null) {
            notificationService.sendBookingNotification(savedBooking);
        }
        if (appNotificationService != null) {
            String vehicleTitle = (vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                    + (vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                    ? " (" + vehicle.getRegistrationNumber() + ")" : "");
            if (vehicle.getOwner() != null) {
                String renterName = renter.getFirstName() != null && !renter.getFirstName().isBlank()
                        ? renter.getFirstName() : "A customer";
                appNotificationService.createNotification(
                        vehicle.getOwner(),
                        "New Booking for " + vehicleTitle,
                        "A new booking has been placed by " + renterName + " for " + vehicleTitle + ".",
                        "BOOKING_CREATED",
                        savedBooking.getId(),
                        "BOOKING"
                );
            }
            appNotificationService.createNotification(
                    renter,
                    "Booking Created for " + vehicleTitle,
                    "Your booking for " + vehicleTitle + " has been placed successfully.",
                    "BOOKING_CREATED",
                    savedBooking.getId(),
                    "BOOKING"
            );
        }
        return BookingResponse.from(savedBooking);
    }

    private void requireDocuments(User renter, BookingMode mode) {
        if (renter.getIdNumber() == null || renter.getIdNumber().isBlank()
                || (!documentRepository.existsByUserIdAndDocumentTypeAndReviewStatus(
                renter.getId(), DocumentType.NATIONAL_ID, DocumentReviewStatus.APPROVED)
                && !documentRepository.existsByUserIdAndDocumentTypeAndReviewStatus(
                renter.getId(), DocumentType.PASSPORT, DocumentReviewStatus.APPROVED))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "An approved national ID or passport and ID number are required before booking");
        }
        if (mode == BookingMode.SELF_DRIVE
                && !documentRepository.existsByUserIdAndDocumentTypeAndReviewStatus(
                renter.getId(), DocumentType.DRIVING_LICENSE, DocumentReviewStatus.APPROVED)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "An approved driving license is required for self-drive bookings");
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(Authentication authentication) {
        return bookingRepository.findByUserEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName())
                .stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> forOwner(Authentication authentication) {
        return bookingRepository.findByVehicleOwnerEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName())
                .stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id, Authentication authentication) {
        Booking booking = find(id);
        boolean renter = booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean owner = booking.getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean manager = has(authentication, "BOOKING_MANAGE");
        if (!renter && !owner && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this booking");
        }
        return BookingResponse.from(booking);
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
            return BookingResponse.from(bookingRepository.save(booking));
        }

    @Transactional
    @Scheduled(fixedDelayString = "${app.booking.pending-expiration-ms:900000}")
    public void expireUnpaidPendingBookings() {
            ZonedDateTime cutoff = ZonedDateTime.now().minusNanos(
                    java.time.Duration.ofMillis(pendingExpirationMs).toNanos());
            bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)
                    .forEach(booking -> {
                        if (paymentRepository == null
                                || !paymentRepository.existsByBookingIdAndStatus(
                                booking.getId(), PaymentStatus.SUCCESSFUL)) {
                            booking.setStatus(BookingStatus.CANCELLED);
                            booking.setDispatcherNotes("Automatically expired before payment");
                            bookingRepository.save(booking);
                        }
                    });
    }

    @Transactional(readOnly = true)
    public BookingReportResponse report(List<Booking> bookings) {
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
        return new BookingReportResponse(now, bookings.size(), upcoming.size(), active.size(),
                completed.size(), cancelled.size(), rejected.size(), gross, platform, payout,
                toResponses(upcoming), toResponses(active), toResponses(completed),
                toResponses(cancelled), toResponses(rejected));
    }

    public BookingReportResponse renterReport(Authentication authentication) {
        return report(bookingRepository.findByUserEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName()));
    }

    public BookingReportResponse ownerReport(Authentication authentication) {
        return report(bookingRepository.findByVehicleOwnerEmailAddressIgnoreCaseOrderByStartTimeDesc(authentication.getName()));
    }

    public BookingReportResponse platformReport() {
        return report(bookingRepository.findAllByOrderByStartTimeDesc());
    }

    private List<BookingResponse> toResponses(List<Booking> bookings) {
        return bookings.stream().map(BookingResponse::from).toList();
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
        if (appNotificationService != null) {
            Vehicle veh = savedBooking.getVehicle();
            String vehicleTitle = veh != null && veh.getModel() != null ? veh.getModel() : "Vehicle";
            if (savedBooking.getUser() != null) {
                appNotificationService.createNotification(
                        savedBooking.getUser(),
                        "Booking " + next.name() + " for " + vehicleTitle,
                        "Your booking for " + vehicleTitle + " is now " + next.name().toLowerCase() + ".",
                        "BOOKING_STATUS_CHANGED",
                        savedBooking.getId(),
                        "BOOKING"
                );
            }
        }
        return BookingResponse.from(savedBooking);
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
        BigDecimal amount = BigDecimal.valueOf(vehicle.getPricePerDay()).multiply(BigDecimal.valueOf(days));
        return new BookingQuoteResponse(vehicle.getId(), days,
                BigDecimal.valueOf(vehicle.getPricePerDay()), amount);
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
        return BookingResponse.from(bookingRepository.save(booking));
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
}
