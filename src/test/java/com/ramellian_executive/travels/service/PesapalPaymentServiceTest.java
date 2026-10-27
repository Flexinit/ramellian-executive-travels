package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.repository.*;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
class PesapalPaymentServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingGroupRepository bookingGroupRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock BookingContractRepository contractRepository;
    @Mock PesapalEventRepository eventRepository;
    @Mock PesapalClient pesapalClient;
    @Mock PlatformSettingsService platformSettingsService;
    @Mock ReceiptService receiptService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock VerificationNotificationService notificationService;
    @Mock Authentication authentication;
    @Mock BookingService bookingService;

    private PesapalPaymentService service;

    @BeforeEach
    void setUp() {
        when(platformSettingsService.getCommissionRatePct()).thenReturn(1.0);
        when(platformSettingsService.getPesapalTransactionFeeRatePct()).thenReturn(0.0);
        when(platformSettingsService.getDeliveryFlatFee()).thenReturn(0.0);
        when(platformSettingsService.getDriverFlatCharge()).thenReturn(0.0);
        when(receiptService.generate(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PesapalPaymentService(
                bookingRepository, bookingGroupRepository, userRepository,
                paymentRepository, contractRepository, eventRepository, pesapalClient,
                null, notificationService, platformSettingsService, receiptService, eventPublisher,
                bookingService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmailAddress(email);
        u.setFirstName("Test");
        u.setLastName("User");
        u.setPhoneNumber("+254700000000");
        return u;
    }

    private Vehicle vehicle(User owner, double pricePerDay) {
        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID());
        v.setOwner(owner);
        v.setPricePerDay(pricePerDay);
        return v;
    }

    private Booking booking(User renter, Vehicle vehicle, ZonedDateTime start, ZonedDateTime end) {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setUser(renter);
        b.setVehicle(vehicle);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setStatus(BookingStatus.PENDING);
        return b;
    }

    private BookingContract acceptedContract(Booking b) {
        BookingContract c = new BookingContract();
        c.setId(UUID.randomUUID());
        c.setBooking(b);
        c.setAcceptedAt(ZonedDateTime.now().minusHours(1));
        c.setContractText("Terms...");
        c.setTemplateVersion(1);
        return c;
    }

    private PesapalClient.PesapalOrder pesapalOrder() {
        return new PesapalClient.PesapalOrder("TRACK-001", "https://pay.pesapal.com/redirect");
    }

    private Payment pendingPayment(Booking b, double amount) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBooking(b);
        p.setAmount(amount);
        p.setPlatformFee(amount * 0.01);
        p.setOwnerPayout(amount * 0.99);
        p.setStatus(PaymentStatus.PENDING);
        p.setProvider("PESAPAL");
        p.setProviderOrderTrackingId("TRACK-001");
        p.setProviderMerchantReference("BOOKING-" + b.getId() + "-" + UUID.randomUUID());
        p.setPaymentMethod("PESAPAL");
        p.setChargeType(PaymentChargeType.RENTAL);
        return p;
    }

    // ── checkout ─────────────────────────────────────────────────────────────────

    @Nested
    class Checkout {

        @Test
        void happyPath_createsPaymentAndReturnsCheckoutResponse() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ZonedDateTime end = start.plusDays(2);
            Booking b = booking(renter, v, start, end);
            BookingContract contract = acceptedContract(b);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.of(contract));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.empty());
            when(pesapalClient.submitOrder(anyString(), anyDouble(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(pesapalOrder());
            Payment saved = pendingPayment(b, 10000.0);
            when(paymentRepository.save(any())).thenReturn(saved);

            PaymentCheckoutResponse response = service.checkout(b.getId(), authentication);

            assertNotNull(response);
            assertEquals("PESAPAL", response.provider());
            assertEquals("TRACK-001", response.orderTrackingId());
            assertEquals("PENDING", response.status());
            verify(pesapalClient).submitOrder(anyString(), eq(10000.0), eq("renter@test.com"),
                    anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void amountCalculation_roundsUpToFullDay() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ZonedDateTime end = start.plusHours(25); // 25h → 2 days rounded up
            Booking b = booking(renter, v, start, end);
            BookingContract contract = acceptedContract(b);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.of(contract));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.empty());
            when(pesapalClient.submitOrder(anyString(), anyDouble(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(pesapalOrder());
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            when(paymentRepository.save(captor.capture())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            service.checkout(b.getId(), authentication);

            // 25h → 2 days → 2 × 5000 = 10000
            assertEquals(10000.0, captor.getValue().getAmount());
        }

        @Test
        void minimumOneDayCharge_forSubDayBooking() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ZonedDateTime end = start.plusHours(5); // < 1 day → still 1 day
            Booking b = booking(renter, v, start, end);
            BookingContract contract = acceptedContract(b);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.of(contract));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.empty());
            when(pesapalClient.submitOrder(anyString(), anyDouble(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(pesapalOrder());
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            when(paymentRepository.save(captor.capture())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            service.checkout(b.getId(), authentication);

            assertEquals(5000.0, captor.getValue().getAmount());
        }

        @Test
        void platformFeeAndOwnerPayoutCorrectlyCalculated() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ZonedDateTime end = start.plusDays(1);
            Booking b = booking(renter, v, start, end);
            BookingContract contract = acceptedContract(b);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.of(contract));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.empty());
            when(pesapalClient.submitOrder(anyString(), anyDouble(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(pesapalOrder());
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            when(paymentRepository.save(captor.capture())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            service.checkout(b.getId(), authentication);

            Payment saved = captor.getValue();
            assertEquals(5000.0, saved.getAmount());
            assertEquals(50.0, saved.getPlatformFee(), 0.001); // 1%
            assertEquals(4950.0, saved.getOwnerPayout(), 0.001);
        }

        @Test
        void idempotency_returnsExistingPendingPayment() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ZonedDateTime end = start.plusDays(1);
            Booking b = booking(renter, v, start, end);
            Payment existing = pendingPayment(b, 5000.0);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.of(acceptedContract(b)));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.of(existing));

            PaymentCheckoutResponse response = service.checkout(b.getId(), authentication);

            assertEquals(existing.getId(), response.paymentId());
            assertEquals("PENDING", response.status());
            verifyNoInteractions(pesapalClient);
        }

        @Test
        void forbiddenForNonRenter() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));

            when(authentication.getName()).thenReturn("other@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkout(b.getId(), authentication));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void notFoundWhenBookingDoesNotExist() {
            UUID bookingId = UUID.randomUUID();
            when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkout(bookingId, authentication));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void conflictWhenContractNotFound() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkout(b.getId(), authentication));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        void conflictWhenContractNotAccepted() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            BookingContract contract = new BookingContract();
            contract.setId(UUID.randomUUID());
            contract.setBooking(b);
            contract.setContractText("Terms");
            contract.setTemplateVersion(1);
            // acceptedAt is null — not yet accepted

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.of(contract));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkout(b.getId(), authentication));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
    }

    // ── processCallback ───────────────────────────────────────────────────────────

    @Nested
    class ProcessCallback {

        @Test
        void singleBooking_successfulPayment_updatesStatusAndConfirmsBooking() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            String ref = payment.getProviderMerchantReference();

            when(paymentRepository.findByProviderMerchantReference(ref)).thenReturn(Optional.of(payment));
            when(eventRepository.existsByOrderTrackingIdAndEventType("TRACK-001", "PAYMENT_STATUS"))
                    .thenReturn(false);
            when(pesapalClient.status("TRACK-001"))
                    .thenReturn(new PesapalClient.PesapalTransaction(1, "COMPLETED", "REC-123"));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processCallback("TRACK-001", ref);

            assertEquals(PaymentStatus.SUCCESSFUL, payment.getStatus());
            assertEquals("REC-123", payment.getProviderReceipt());
            assertEquals(BookingStatus.CONFIRMED, b.getStatus());
            verify(bookingRepository).save(b);
            verify(eventRepository).save(any(PesapalEvent.class));
        }

        @Test
        void singleBooking_failedPayment_setsStatusFailed() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            String ref = payment.getProviderMerchantReference();

            when(paymentRepository.findByProviderMerchantReference(ref)).thenReturn(Optional.of(payment));
            when(eventRepository.existsByOrderTrackingIdAndEventType("TRACK-001", "PAYMENT_STATUS"))
                    .thenReturn(false);
            when(pesapalClient.status("TRACK-001"))
                    .thenReturn(new PesapalClient.PesapalTransaction(2, "FAILED", ""));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processCallback("TRACK-001", ref);

            assertEquals(PaymentStatus.FAILED, payment.getStatus());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        void idempotency_skipsDuplicateIpnEvent() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            String ref = payment.getProviderMerchantReference();

            when(paymentRepository.findByProviderMerchantReference(ref)).thenReturn(Optional.of(payment));
            when(eventRepository.existsByOrderTrackingIdAndEventType("TRACK-001", "PAYMENT_STATUS"))
                    .thenReturn(true);

            service.processCallback("TRACK-001", ref);

            verifyNoInteractions(pesapalClient);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void alreadySuccessful_skipsProcessing() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            String ref = payment.getProviderMerchantReference();

            when(paymentRepository.findByProviderMerchantReference(ref)).thenReturn(Optional.of(payment));

            service.processCallback("TRACK-001", ref);

            verifyNoInteractions(pesapalClient);
            verifyNoInteractions(eventRepository);
        }

        @Test
        void groupPayment_successfulPayment_updatesAllBookingsInGroup() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b1 = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Booking b2 = booking(renter, v, ZonedDateTime.now().plusDays(3), ZonedDateTime.now().plusDays(4));

            UUID groupId = UUID.randomUUID();
            String groupRef = "GROUP-" + groupId + "-uuid-B" + b1.getId();

            Payment p1 = pendingPayment(b1, 5000.0);
            p1.setProviderOrderTrackingId("GROUP-TRACK");
            p1.setProviderMerchantReference(groupRef);
            Payment p2 = pendingPayment(b2, 5000.0);
            p2.setProviderOrderTrackingId("GROUP-TRACK");
            p2.setProviderMerchantReference("GROUP-" + groupId + "-uuid-B" + b2.getId());

            when(paymentRepository.findAllByProviderOrderTrackingId("GROUP-TRACK"))
                    .thenReturn(List.of(p1, p2));
            when(eventRepository.existsByOrderTrackingIdAndEventType("GROUP-TRACK", "PAYMENT_STATUS"))
                    .thenReturn(false);
            when(pesapalClient.status("GROUP-TRACK"))
                    .thenReturn(new PesapalClient.PesapalTransaction(1, "COMPLETED", "REC-GROUP"));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processCallback("GROUP-TRACK", groupRef);

            assertEquals(PaymentStatus.SUCCESSFUL, p1.getStatus());
            assertEquals(PaymentStatus.SUCCESSFUL, p2.getStatus());
            assertEquals(BookingStatus.CONFIRMED, b1.getStatus());
            assertEquals(BookingStatus.CONFIRMED, b2.getStatus());
        }

        @Test
        void overstayPayment_successfulPayment_updatesStatus() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1));

            String overstayRef = "OVERSTAY-" + b.getId() + "-" + UUID.randomUUID();
            Payment overstayPayment = new Payment();
            overstayPayment.setId(UUID.randomUUID());
            overstayPayment.setBooking(b);
            overstayPayment.setAmount(2500.0);
            overstayPayment.setPlatformFee(25.0);
            overstayPayment.setOwnerPayout(2475.0);
            overstayPayment.setStatus(PaymentStatus.PENDING);
            overstayPayment.setProvider("PESAPAL");
            overstayPayment.setProviderOrderTrackingId("OVER-TRACK");
            overstayPayment.setProviderMerchantReference(overstayRef);
            overstayPayment.setChargeType(PaymentChargeType.OVERSTAY);

            when(paymentRepository.findByProviderMerchantReference(overstayRef))
                    .thenReturn(Optional.of(overstayPayment));
            when(eventRepository.existsByOrderTrackingIdAndEventType("OVER-TRACK", "PAYMENT_STATUS"))
                    .thenReturn(false);
            when(pesapalClient.status("OVER-TRACK"))
                    .thenReturn(new PesapalClient.PesapalTransaction(1, "COMPLETED", "REC-OVER"));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processCallback("OVER-TRACK", overstayRef);

            assertEquals(PaymentStatus.SUCCESSFUL, overstayPayment.getStatus());
            assertEquals("REC-OVER", overstayPayment.getProviderReceipt());
        }

        @Test
        void mismatchedTrackingId_throwsBadRequest() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            String ref = payment.getProviderMerchantReference();

            when(paymentRepository.findByProviderMerchantReference(ref)).thenReturn(Optional.of(payment));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.processCallback("WRONG-TRACK", ref));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        void pendingTransaction_noEvent_doesNotSaveEvent() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            String ref = payment.getProviderMerchantReference();

            when(paymentRepository.findByProviderMerchantReference(ref)).thenReturn(Optional.of(payment));
            when(eventRepository.existsByOrderTrackingIdAndEventType("TRACK-001", "PAYMENT_STATUS"))
                    .thenReturn(false);
            when(pesapalClient.status("TRACK-001"))
                    .thenReturn(new PesapalClient.PesapalTransaction(0, "PENDING", ""));

            service.processCallback("TRACK-001", ref);

            verify(eventRepository, never()).save(any());
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
        }
    }

    // ── status ────────────────────────────────────────────────────────────────────

    @Nested
    class Status {

        @Test
        void renter_canViewOwnPaymentStatus() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            payment.setStatus(PaymentStatus.SUCCESSFUL);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(authentication.getAuthorities()).thenReturn(List.of());
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.of(payment));

            PaymentStatusResponse response = service.status(b.getId(), authentication);

            assertEquals(payment.getId(), response.paymentId());
            assertEquals("SUCCESSFUL", response.status());
        }

        @Test
        void owner_canViewPaymentStatus() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            payment.setStatus(PaymentStatus.SUCCESSFUL);

            when(authentication.getName()).thenReturn("owner@test.com");
            when(authentication.getAuthorities()).thenReturn(List.of());
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.of(payment));

            PaymentStatusResponse response = service.status(b.getId(), authentication);

            assertNotNull(response);
        }

        @Test
        void manager_canViewPaymentStatus() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            payment.setStatus(PaymentStatus.SUCCESSFUL);

            when(authentication.getName()).thenReturn("manager@test.com");
            GrantedAuthority authority = () -> "PAYMENT_MANAGE";
            when(authentication.getAuthorities()).thenAnswer(inv -> List.of(authority));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.of(payment));

            PaymentStatusResponse response = service.status(b.getId(), authentication);

            assertNotNull(response);
        }

        @Test
        void unauthorizedUser_throwsForbidden() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);

            when(authentication.getName()).thenReturn("stranger@test.com");
            when(authentication.getAuthorities()).thenReturn(List.of());
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.of(payment));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.status(b.getId(), authentication));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void pendingPesapalPayment_triggersCallbackAndRefreshesStatus() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Payment payment = pendingPayment(b, 5000.0);
            String merchantRef = payment.getProviderMerchantReference();

            when(authentication.getName()).thenReturn("renter@test.com");
            when(authentication.getAuthorities()).thenReturn(List.of());
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.RENTAL))
                    .thenReturn(Optional.of(payment));
            // processCallback internally looks up by merchantRef
            when(paymentRepository.findByProviderMerchantReference(merchantRef))
                    .thenReturn(Optional.of(payment));
            when(eventRepository.existsByOrderTrackingIdAndEventType("TRACK-001", "PAYMENT_STATUS"))
                    .thenReturn(false);
            when(pesapalClient.status("TRACK-001"))
                    .thenReturn(new PesapalClient.PesapalTransaction(1, "COMPLETED", "REC-789"));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Payment refreshed = pendingPayment(b, 5000.0);
            refreshed.setId(payment.getId());
            refreshed.setStatus(PaymentStatus.SUCCESSFUL);
            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(refreshed));

            PaymentStatusResponse response = service.status(b.getId(), authentication);

            verify(pesapalClient).status("TRACK-001");
            assertEquals("SUCCESSFUL", response.status());
        }

        @Test
        void paymentNotFound_throwsNotFound() {
            UUID bookingId = UUID.randomUUID();
            when(paymentRepository.findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL))
                    .thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.status(bookingId, authentication));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    // ── checkoutGroup ─────────────────────────────────────────────────────────────

    @Nested
    class CheckoutGroup {

        @Test
        void happyPath_createsPaymentsForAllBookingsInGroup() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b1 = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));
            Booking b2 = booking(renter, v, ZonedDateTime.now().plusDays(3), ZonedDateTime.now().plusDays(4));

            BookingGroup group = new BookingGroup();
            group.setId(UUID.randomUUID());
            group.setRenter(renter);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
            when(bookingRepository.findByBookingGroupId(group.getId())).thenReturn(List.of(b1, b2));
            when(contractRepository.findByBookingId(b1.getId())).thenReturn(Optional.of(acceptedContract(b1)));
            when(contractRepository.findByBookingId(b2.getId())).thenReturn(Optional.of(acceptedContract(b2)));
            when(paymentRepository.findFirstByProviderMerchantReferenceStartingWith(anyString()))
                    .thenReturn(Optional.empty());
            when(pesapalClient.submitOrder(anyString(), anyDouble(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new PesapalClient.PesapalOrder("GROUP-TRACK", "https://redirect"));
            when(paymentRepository.findByBookingIdAndChargeType(any(), eq(PaymentChargeType.RENTAL)))
                    .thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                if (p.getId() == null) p.setId(UUID.randomUUID());
                return p;
            });

            PaymentCheckoutResponse response = service.checkoutGroup(group.getId(), authentication);

            assertNotNull(response);
            assertEquals("PESAPAL", response.provider());
            assertEquals("GROUP-TRACK", response.orderTrackingId());
            // total = 2 × 5000 = 10000
            verify(pesapalClient).submitOrder(anyString(), eq(10000.0), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString());
            verify(paymentRepository, times(2)).save(any());
        }

        @Test
        void forbiddenForNonRenter() {
            User renter = user("renter@test.com");
            BookingGroup group = new BookingGroup();
            group.setId(UUID.randomUUID());
            group.setRenter(renter);

            when(authentication.getName()).thenReturn("other@test.com");
            when(bookingGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkoutGroup(group.getId(), authentication));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void notFound_whenGroupDoesNotExist() {
            UUID groupId = UUID.randomUUID();
            when(bookingGroupRepository.findById(groupId)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkoutGroup(groupId, authentication));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void conflict_whenGroupHasNoBookings() {
            User renter = user("renter@test.com");
            BookingGroup group = new BookingGroup();
            group.setId(UUID.randomUUID());
            group.setRenter(renter);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
            when(bookingRepository.findByBookingGroupId(group.getId())).thenReturn(List.of());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkoutGroup(group.getId(), authentication));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        void idempotency_returnsExistingPendingGroupPayment() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            Booking b = booking(renter, v, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2));

            BookingGroup group = new BookingGroup();
            group.setId(UUID.randomUUID());
            group.setRenter(renter);

            Payment existing = pendingPayment(b, 5000.0);
            existing.setProviderMerchantReference("GROUP-" + group.getId() + "-uuid-B" + b.getId());

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
            when(bookingRepository.findByBookingGroupId(group.getId())).thenReturn(List.of(b));
            when(contractRepository.findByBookingId(b.getId())).thenReturn(Optional.of(acceptedContract(b)));
            when(paymentRepository.findFirstByProviderMerchantReferenceStartingWith(anyString()))
                    .thenReturn(Optional.of(existing));

            PaymentCheckoutResponse response = service.checkoutGroup(group.getId(), authentication);

            assertEquals(existing.getId(), response.paymentId());
            verifyNoInteractions(pesapalClient);
        }
    }

    // ── checkoutOverstay ──────────────────────────────────────────────────────────

    @Nested
    class CheckoutOverstay {

        @Test
        void happyPath_initiatesPesapalForOverstay() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().minusDays(3);
            ZonedDateTime end = ZonedDateTime.now().minusDays(1);
            Booking b = booking(renter, v, start, end);
            b.setActualEndTime(end.plusHours(2));

            Payment overstay = new Payment();
            overstay.setId(UUID.randomUUID());
            overstay.setBooking(b);
            overstay.setAmount(2500.0);
            overstay.setPlatformFee(25.0);
            overstay.setOwnerPayout(2475.0);
            overstay.setStatus(PaymentStatus.PENDING);
            overstay.setChargeType(PaymentChargeType.OVERSTAY);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.OVERSTAY))
                    .thenReturn(Optional.of(overstay));
            when(pesapalClient.submitOrder(anyString(), anyDouble(), anyString(),
                    anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new PesapalClient.PesapalOrder("OVER-TRACK", "https://redirect"));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PaymentCheckoutResponse response = service.checkoutOverstay(b.getId(), authentication);

            assertNotNull(response);
            assertEquals("PESAPAL", response.provider());
            assertEquals("OVER-TRACK", response.orderTrackingId());
            verify(pesapalClient).submitOrder(
                    argThat(ref -> ref.startsWith("OVERSTAY-")),
                    eq(2500.0), eq("renter@test.com"),
                    anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void conflict_whenNoOverstayRecorded() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().minusDays(3);
            ZonedDateTime end = ZonedDateTime.now().minusDays(1);
            Booking b = booking(renter, v, start, end);
            // actualEndTime is null — no overstay

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkoutOverstay(b.getId(), authentication));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        void conflict_whenOverstayAlreadyPaid() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().minusDays(3);
            ZonedDateTime end = ZonedDateTime.now().minusDays(1);
            Booking b = booking(renter, v, start, end);
            b.setActualEndTime(end.plusHours(2));

            Payment overstay = new Payment();
            overstay.setId(UUID.randomUUID());
            overstay.setBooking(b);
            overstay.setAmount(2500.0);
            overstay.setStatus(PaymentStatus.SUCCESSFUL);
            overstay.setChargeType(PaymentChargeType.OVERSTAY);

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.OVERSTAY))
                    .thenReturn(Optional.of(overstay));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkoutOverstay(b.getId(), authentication));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        void idempotency_returnsExistingPendingOverstayCheckout() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().minusDays(3);
            ZonedDateTime end = ZonedDateTime.now().minusDays(1);
            Booking b = booking(renter, v, start, end);
            b.setActualEndTime(end.plusHours(2));

            Payment overstay = new Payment();
            overstay.setId(UUID.randomUUID());
            overstay.setBooking(b);
            overstay.setAmount(2500.0);
            overstay.setStatus(PaymentStatus.PENDING);
            overstay.setChargeType(PaymentChargeType.OVERSTAY);
            overstay.setProviderOrderTrackingId("EXISTING-TRACK");
            overstay.setTransactionReference("https://redirect");

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.OVERSTAY))
                    .thenReturn(Optional.of(overstay));

            PaymentCheckoutResponse response = service.checkoutOverstay(b.getId(), authentication);

            assertEquals(overstay.getId(), response.paymentId());
            assertEquals("EXISTING-TRACK", response.orderTrackingId());
            verifyNoInteractions(pesapalClient);
        }

        @Test
        void notFound_whenNoOverstayChargeExists() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().minusDays(3);
            ZonedDateTime end = ZonedDateTime.now().minusDays(1);
            Booking b = booking(renter, v, start, end);
            b.setActualEndTime(end.plusHours(2));

            when(authentication.getName()).thenReturn("renter@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(paymentRepository.findByBookingIdAndChargeType(b.getId(), PaymentChargeType.OVERSTAY))
                    .thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkoutOverstay(b.getId(), authentication));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void forbiddenForNonRenter() {
            User renter = user("renter@test.com");
            User owner = user("owner@test.com");
            Vehicle v = vehicle(owner, 5000.0);
            ZonedDateTime start = ZonedDateTime.now().minusDays(3);
            ZonedDateTime end = ZonedDateTime.now().minusDays(1);
            Booking b = booking(renter, v, start, end);
            b.setActualEndTime(end.plusHours(2));

            when(authentication.getName()).thenReturn("other@test.com");
            when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service.checkoutOverstay(b.getId(), authentication));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }
    }
}
