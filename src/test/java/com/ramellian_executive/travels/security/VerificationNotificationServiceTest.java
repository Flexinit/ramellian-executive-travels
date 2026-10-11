package com.ramellian_executive.travels.security;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.BookingMode;
import com.ramellian_executive.travels.entity.TripType;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.repository.vehicle.VehiclePhotoRepository;
import com.ramellian_executive.travels.service.EmailTemplateService;
import com.ramellian_executive.travels.service.ReceiptService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationNotificationServiceTest {

    private JavaMailSender mailSender;
    private EmailTemplateService templates;
    private VerificationNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        templates = mock(EmailTemplateService.class);
        service = new VerificationNotificationService(mailSender, mock(RestClient.Builder.class),
                mock(VehiclePhotoRepository.class), templates, mock(ReceiptService.class));
    }

    private MimeMessage stubMimeMessage() {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(msg);
        return msg;
    }

    @Test
    void notificationDeliveryMethodsAreAsynchronous() throws NoSuchMethodException {
        Async emailAsync = VerificationNotificationService.class
                .getMethod("sendEmail", String.class, String.class, String.class)
                .getAnnotation(Async.class);
        Async bookingAsync = VerificationNotificationService.class
                .getMethod("sendBookingNotification", Booking.class, double.class)
                .getAnnotation(Async.class);
        Async whatsappAsync = VerificationNotificationService.class
                .getMethod("sendWhatsApp", String.class, String.class)
                .getAnnotation(Async.class);

        assertEquals("notificationTaskExecutor", emailAsync.value());
        assertEquals("notificationTaskExecutor", bookingAsync.value());
        assertEquals("notificationTaskExecutor", whatsappAsync.value());
    }

    @Test
    void blankFromAddressDoesNotCreateMalformedMail() throws Exception {
        ReflectionTestUtils.setField(service, "fromEmail", " ");
        when(templates.verificationCode(any(), any(), any())).thenReturn("<p>code</p>");
        MimeMessage mime = stubMimeMessage();

        service.sendEmail("person@example.com", "123456", "email verification");

        verify(mailSender).send(mime);
        assertNull(mime.getFrom());
        assertEquals("person@example.com", mime.getAllRecipients()[0].toString());
    }

    @Test
    void sendBookingNotificationSendsEmailToOwner() throws Exception {
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@ramellian.com");

        User owner = new User();
        owner.setFirstName("Alice");
        owner.setEmailAddress("owner@example.com");

        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setModel("Toyota Prado");
        vehicle.setRegistrationNumber("KDD 123A");
        vehicle.setOwner(owner);

        User renter = new User();
        renter.setFirstName("Bob");
        renter.setLastName("Smith");
        renter.setEmailAddress("renter@example.com");

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setVehicle(vehicle);
        booking.setUser(renter);
        booking.setStartTime(ZonedDateTime.now().plusDays(1));
        booking.setEndTime(ZonedDateTime.now().plusDays(3));
        booking.setBookingMode(BookingMode.SELF_DRIVE);
        booking.setTripType(TripType.AIRPORT_TRANSFER);
        booking.setRenterNotes("Early pickup preferred");

        when(templates.bookingCreatedOwner(eq(booking), any(), eq(2.0))).thenReturn("<p>booking</p>");
        MimeMessage mime = stubMimeMessage();

        service.sendBookingNotification(booking, 2.0);

        verify(mailSender).send(mime);
        assertTrue(mime.getSubject().contains("Toyota Prado"));
        assertTrue(mime.getSubject().contains("KDD 123A"));
        assertEquals("owner@example.com", mime.getAllRecipients()[0].toString());
        verify(templates).bookingCreatedOwner(eq(booking), any(), eq(2.0));
    }

    @Test
    void sendBookingNotificationSkipsWhenOwnerEmailMissing() {
        Booking booking = new Booking();
        Vehicle vehicle = new Vehicle();
        booking.setVehicle(vehicle);

        service.sendBookingNotification(booking, 2.0);
        verifyNoInteractions(mailSender);

        User owner = new User();
        owner.setEmailAddress("");
        vehicle.setOwner(owner);
        service.sendBookingNotification(booking, 2.0);
        verifyNoInteractions(mailSender);
    }
}
