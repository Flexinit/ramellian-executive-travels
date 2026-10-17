package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.*;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingGroupRepository bookingGroupRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
    @Mock UserDocumentRepository documentRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock BookingContractRepository contractRepository;
    @Mock com.ramellian_executive.travels.service.vehicle.VehicleReviewService reviewService;
    @Mock VerificationNotificationService notificationService;
    @Mock NotificationService appNotificationService;
    @Mock PlatformSettingsService platformSettingsService;
    @Mock Authentication authentication;

    private BookingService service;

    @BeforeEach
    void setUp() {
        when(platformSettingsService.getCommissionRatePct()).thenReturn(1.0);
        when(platformSettingsService.getPesapalTransactionFeeRatePct()).thenReturn(1.0);
        when(platformSettingsService.getDeliveryFlatFee()).thenReturn(0.0);
        when(platformSettingsService.getDriverFlatCharge()).thenReturn(0.0);
        service = new BookingService(
                bookingRepository, bookingGroupRepository,
                vehicleRepository, userRepository, documentRepository,
                paymentRepository, contractRepository,
                notificationService, appNotificationService,
                platformSettingsService, reviewService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmailAddress(email);
        u.setFirstName("Test");
        u.setLastName("User");
        return u;
    }

    private Vehicle approvedVehicle(UUID id, User owner) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setAvailable(true);
        v.setApprovalStatus(VehicleApprovalStatus.APPROVED);
        v.setSelfDrive(true);
        v.setChauffeured(true);
        v.setPricePerDay(100.0);
        v.setModel("Test Vehicle");
        v.setOwner(owner);
        return v;
    }

    private Booking booking(User renter, Vehicle vehicle, BookingStatus status) {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setUser(renter);
        b.setVehicle(vehicle);
        b.setStatus(status);
        b.setBookingMode(BookingMode.SELF_DRIVE);
        b.setStartTime(ZonedDateTime.now().plusDays(1));
        b.setEndTime(ZonedDateTime.now().plusDays(3));
        return b;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void asRenter(User renter) {
        when(authentication.getName()).thenReturn(renter.getEmailAddress());
        when((Collection) authentication.getAuthorities()).thenReturn(List.of());
        when(userRepository.findByEmailAddressIgnoreCase(renter.getEmailAddress()))
                .thenReturn(Optional.of(renter));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void asOwner(User owner) {
        when(authentication.getName()).thenReturn(owner.getEmailAddress());
        when((Collection) authentication.getAuthorities()).thenReturn(List.of());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void asStranger(User stranger) {
        when(authentication.getName()).thenReturn(stranger.getEmailAddress());
        when((Collection) authentication.getAuthorities()).thenReturn(List.of());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void withManager() {
        when((Collection) authentication.getAuthorities())
                .thenReturn(List.of(new SimpleGrantedAuthority("BOOKING_MANAGE")));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void happyPath_returnsPendingBooking() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User renter = user("renter@test.com");
            Vehicle v = approvedVehicle(vId, owner);

            asRenter(renter);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));
            when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            BookingResponse resp = service.create(
                    new BookingRequest(vId, start, start.plusDays(2), "SELF_DRIVE", null, null, null),
                    authentication);

            assertNotNull(resp);
            assertEquals("PENDING", resp.status());
            assertEquals(renter.getEmailAddress(), resp.renterEmail());
            verify(bookingRepository).save(any(Booking.class));
        }

        @Test
        void endTimeBeforeStart_throws400() {
            ZonedDateTime start = ZonedDateTime.now().plusDays(2);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(UUID.randomUUID(), start, start.minusHours(1),
                                    "SELF_DRIVE", null, null, null),
                            authentication));
            assertEquals(400, ex.getStatusCode().value());
        }

        @Test
        void invalidMode_throws400() {
            User renter = user("renter@test.com");
            asRenter(renter);
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(UUID.randomUUID(), start, start.plusDays(1),
                                    "HELICOPTER", null, null, null),
                            authentication));
            assertEquals(400, ex.getStatusCode().value());
        }

        @Test
        void vehicleUnavailable_throws409() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User renter = user("renter@test.com");
            Vehicle v = approvedVehicle(vId, owner);
            v.setAvailable(false);

            asRenter(renter);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(vId, start, start.plusDays(1),
                                    "SELF_DRIVE", null, null, null),
                            authentication));
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        void vehicleNotApproved_throws409() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User renter = user("renter@test.com");
            Vehicle v = approvedVehicle(vId, owner);
            v.setApprovalStatus(VehicleApprovalStatus.PENDING);

            asRenter(renter);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(vId, start, start.plusDays(1),
                                    "SELF_DRIVE", null, null, null),
                            authentication));
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        void overlappingBooking_throws409_andNeverSaves() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User renter = user("renter@test.com");
            Vehicle v = approvedVehicle(vId, owner);

            asRenter(renter);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));
            when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(true);

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(vId, start, start.plusDays(2),
                                    "SELF_DRIVE", null, null, null),
                            authentication));
            verify(bookingRepository, never()).save(any());
        }

        @Test
        void selfDriveOnChauffeuredOnlyVehicle_throws409() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User renter = user("renter@test.com");
            Vehicle v = approvedVehicle(vId, owner);
            v.setSelfDrive(false);

            asRenter(renter);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));
            when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(vId, start, start.plusDays(1),
                                    "SELF_DRIVE", null, null, null),
                            authentication));
        }

        @Test
        void chauffeuredOnSelfDriveOnlyVehicle_throws409() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User renter = user("renter@test.com");
            Vehicle v = approvedVehicle(vId, owner);
            v.setChauffeured(false);

            asRenter(renter);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));
            when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(vId, start, start.plusDays(1),
                                    "CHAUFFEURED", null, null, null),
                            authentication));
        }

        @Test
        void staffBookingWithRenterId_usesSpecifiedRenter() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User walkin = user("walkin@test.com");
            User staff = user("staff@test.com");
            Vehicle v = approvedVehicle(vId, owner);

            when(authentication.getName()).thenReturn(staff.getEmailAddress());
            withManager();
            when(userRepository.findById(walkin.getId())).thenReturn(Optional.of(walkin));
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));
            when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            BookingResponse resp = service.create(
                    new BookingRequest(vId, start, start.plusDays(1),
                            "SELF_DRIVE", null, null, walkin.getId()),
                    authentication);

            assertEquals(walkin.getEmailAddress(), resp.renterEmail());
        }

        @Test
        void renterIdWithoutPermission_throws403() {
            when(authentication.getAuthorities()).thenReturn(List.of());
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.create(
                            new BookingRequest(UUID.randomUUID(), start, start.plusDays(1),
                                    "SELF_DRIVE", null, null, UUID.randomUUID()),
                            authentication));
            assertEquals(403, ex.getStatusCode().value());
        }

        @Test
        void noEmailNotificationsOnCreation() {
            UUID vId = UUID.randomUUID();
            User owner = user("owner@test.com");
            User renter = user("renter@test.com");
            Vehicle v = approvedVehicle(vId, owner);

            asRenter(renter);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));
            when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            service.create(new BookingRequest(vId, start, start.plusDays(2),
                    "SELF_DRIVE", TripType.GENERAL, "Notes", null), authentication);

            verifyNoInteractions(notificationService);
        }
    }

    // ── STATUS TRANSITIONS ────────────────────────────────────────────────────────

    @Nested
    class StatusChange {

        @Test
        void renterCancelsPending_allowed() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asRenter(renter);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.changeStatus(b.getId(),
                    new BookingStatusRequest("CANCELLED", null), authentication);
            assertEquals("CANCELLED", resp.status());
        }

        @Test
        void ownerCancelsPending_allowed() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.changeStatus(b.getId(),
                    new BookingStatusRequest("CANCELLED", null), authentication);
            assertEquals("CANCELLED", resp.status());
        }

        @Test
        void strangerCancels_throws403() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            User stranger = user("nobody@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asStranger(stranger);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.changeStatus(b.getId(),
                            new BookingStatusRequest("CANCELLED", null), authentication));
            assertEquals(403, ex.getStatusCode().value());
        }

        @Test
        void confirmWithoutPayment_throws409() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(paymentRepository.existsByBookingIdAndStatus(b.getId(), PaymentStatus.SUCCESSFUL))
                    .thenReturn(false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.changeStatus(b.getId(),
                            new BookingStatusRequest("CONFIRMED", null), authentication));
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        void confirmWithPayment_succeeds() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(paymentRepository.existsByBookingIdAndStatus(b.getId(), PaymentStatus.SUCCESSFUL))
                    .thenReturn(true);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.changeStatus(b.getId(),
                    new BookingStatusRequest("CONFIRMED", null), authentication);
            assertEquals("CONFIRMED", resp.status());
        }

        @Test
        void pendingToActive_invalidTransition_throws409() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.changeStatus(b.getId(),
                            new BookingStatusRequest("ACTIVE", null), authentication));
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        void confirmedToActive_byOwner_succeeds() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.CONFIRMED);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.changeStatus(b.getId(),
                    new BookingStatusRequest("ACTIVE", null), authentication);
            assertEquals("ACTIVE", resp.status());
        }

        @Test
        void activeToCompleted_byOwner_succeeds() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.ACTIVE);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.changeStatus(b.getId(),
                    new BookingStatusRequest("COMPLETED", null), authentication);
            assertEquals("COMPLETED", resp.status());
        }

        @Test
        void completedToCancelled_invalidTransition_throws409() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.COMPLETED);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asRenter(renter);

            assertThrows(ResponseStatusException.class,
                    () -> service.changeStatus(b.getId(),
                            new BookingStatusRequest("CANCELLED", null), authentication));
        }

        @Test
        void strangerConfirming_throws403() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            User stranger = user("nobody@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asStranger(stranger);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.changeStatus(b.getId(),
                            new BookingStatusRequest("CONFIRMED", null), authentication));
            assertEquals(403, ex.getStatusCode().value());
        }

        @Test
        void managerCanConfirmAnyBooking() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            User manager = user("manager@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(authentication.getName()).thenReturn(manager.getEmailAddress());
            withManager();
            when(paymentRepository.existsByBookingIdAndStatus(b.getId(), PaymentStatus.SUCCESSFUL))
                    .thenReturn(true);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.changeStatus(b.getId(),
                    new BookingStatusRequest("CONFIRMED", null), authentication);
            assertEquals("CONFIRMED", resp.status());
        }
    }

    // ── RETURN ────────────────────────────────────────────────────────────────────

    @Nested
    class Return {

        @Test
        void activeBooking_markedCompleted() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.ACTIVE);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.recordReturn(b.getId(),
                    new BookingReturnRequest(b.getEndTime().minusHours(1)), authentication);
            assertEquals("COMPLETED", resp.status());
        }

        @Test
        void onTimeReturn_noOverstayPayment() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.ACTIVE);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.recordReturn(b.getId(),
                    new BookingReturnRequest(b.getEndTime().minusHours(2)), authentication);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void overstay_createsPendingPaymentWithCorrectAmount() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            v.setPricePerDay(200.0);
            Booking b = booking(renter, v, BookingStatus.ACTIVE);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.OVERSTAY))
                    .thenReturn(Optional.empty());
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // 49 h overstay → ceil to 3 days → 600.0
            service.recordReturn(b.getId(),
                    new BookingReturnRequest(b.getEndTime().plusHours(49)), authentication);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            Payment p = captor.getValue();
            assertEquals(PaymentChargeType.OVERSTAY, p.getChargeType());
            assertEquals(PaymentStatus.PENDING, p.getStatus());
            assertEquals(600.0, p.getAmount(), 0.01);
        }

        @Test
        void overstayAlreadyCharged_noSecondPayment() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.ACTIVE);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.OVERSTAY))
                    .thenReturn(Optional.of(new Payment()));
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.recordReturn(b.getId(),
                    new BookingReturnRequest(b.getEndTime().plusDays(2)), authentication);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void pendingBooking_throws409() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.recordReturn(b.getId(),
                            new BookingReturnRequest(ZonedDateTime.now().plusDays(4)), authentication));
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        void byStranger_throws403() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            User stranger = user("nobody@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.ACTIVE);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asStranger(stranger);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.recordReturn(b.getId(),
                            new BookingReturnRequest(ZonedDateTime.now().plusDays(4)), authentication));
            assertEquals(403, ex.getStatusCode().value());
        }
    }

    // ── QUOTE ─────────────────────────────────────────────────────────────────────

    @Nested
    class Quote {

        @Test
        void exactDays_calculatesCorrectly() {
            UUID vId = UUID.randomUUID();
            Vehicle v = approvedVehicle(vId, null);
            v.setPricePerDay(150.0);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            BookingQuoteResponse resp = service.quote(
                    new BookingQuoteRequest(vId, start, start.plusDays(3)));

            assertEquals(3L, resp.numberOfDays());
            assertEquals(454.5, resp.totalAmount().doubleValue(), 0.01); // 450 base + 1% fee
        }

        @Test
        void partialDay_roundsUpToFullDay() {
            UUID vId = UUID.randomUUID();
            Vehicle v = approvedVehicle(vId, null);
            v.setPricePerDay(100.0);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            BookingQuoteResponse resp = service.quote(
                    new BookingQuoteRequest(vId, start, start.plusHours(25)));

            assertEquals(2L, resp.numberOfDays());
            assertEquals(202.0, resp.totalAmount().doubleValue(), 0.01); // 200 base + 1% fee
        }

        @Test
        void lessThan24Hours_chargedAsOneDay() {
            UUID vId = UUID.randomUUID();
            Vehicle v = approvedVehicle(vId, null);
            v.setPricePerDay(80.0);
            when(vehicleRepository.findById(vId)).thenReturn(Optional.of(v));

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            BookingQuoteResponse resp = service.quote(
                    new BookingQuoteRequest(vId, start, start.plusHours(3)));

            assertEquals(1L, resp.numberOfDays());
            assertEquals(80.8, resp.totalAmount().doubleValue(), 0.01); // 80 base + 1% fee
        }

        @Test
        void vehicleNotFound_throws404() {
            UUID vId = UUID.randomUUID();
            when(vehicleRepository.findById(vId)).thenReturn(Optional.empty());

            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.quote(new BookingQuoteRequest(vId, start, start.plusDays(1))));
            assertEquals(404, ex.getStatusCode().value());
        }
    }

    // ── CHAUFFEUR ASSIGNMENT ──────────────────────────────────────────────────────

    @Nested
    class ChauffeurAssignment {

        @Test
        void selfDriveBooking_throws409() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);
            b.setBookingMode(BookingMode.SELF_DRIVE);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.assignChauffeur(b.getId(), UUID.randomUUID(), authentication));
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        void userWithoutChauffeurRole_throws400() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);
            b.setBookingMode(BookingMode.CHAUFFEURED);

            User notChauffeur = user("notchauffeur@test.com");
            notChauffeur.setEnabled(true);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(userRepository.findById(notChauffeur.getId())).thenReturn(Optional.of(notChauffeur));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.assignChauffeur(b.getId(), notChauffeur.getId(), authentication));
            assertEquals(400, ex.getStatusCode().value());
        }

        @Test
        void chauffeurUnavailableForPeriod_throws409() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);
            b.setBookingMode(BookingMode.CHAUFFEURED);

            User chauffeur = user("chauffeur@test.com");
            chauffeur.setEnabled(true);
            UserRole role = new UserRole();
            role.setRoleName(UserRole.CHAUFFEUR);
            chauffeur.getRoles().add(role);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(userRepository.findById(chauffeur.getId())).thenReturn(Optional.of(chauffeur));
            when(bookingRepository.existsOverlappingChauffeurBooking(
                    eq(chauffeur.getId()), eq(b.getId()), any(), any(), any())).thenReturn(true);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.assignChauffeur(b.getId(), chauffeur.getId(), authentication));
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        void happyPath_setsChauffeurOnBooking() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);
            b.setBookingMode(BookingMode.CHAUFFEURED);

            User chauffeur = user("chauffeur@test.com");
            chauffeur.setEnabled(true);
            UserRole role = new UserRole();
            role.setRoleName(UserRole.CHAUFFEUR);
            chauffeur.getRoles().add(role);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asOwner(owner);
            when(userRepository.findById(chauffeur.getId())).thenReturn(Optional.of(chauffeur));
            when(bookingRepository.existsOverlappingChauffeurBooking(
                    eq(chauffeur.getId()), eq(b.getId()), any(), any(), any())).thenReturn(false);
            when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            BookingResponse resp = service.assignChauffeur(b.getId(), chauffeur.getId(), authentication);
            assertEquals(chauffeur.getEmailAddress(), resp.chauffeurEmail());
        }

        @Test
        void byStranger_throws403() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            User stranger = user("nobody@test.com");
            Vehicle v = approvedVehicle(UUID.randomUUID(), owner);
            Booking b = booking(renter, v, BookingStatus.PENDING);
            b.setBookingMode(BookingMode.CHAUFFEURED);

            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            asStranger(stranger);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.assignChauffeur(b.getId(), UUID.randomUUID(), authentication));
            assertEquals(403, ex.getStatusCode().value());
        }
    }
}
