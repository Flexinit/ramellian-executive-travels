package com.ramellian_executive.travels.security;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.BookingMode;
import com.ramellian_executive.travels.entity.TripType;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationNotificationServiceTest {

    private JavaMailSender mailSender;
    private VerificationNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        service = new VerificationNotificationService(mailSender, mock(RestClient.Builder.class));
    }

    @Test
    void notificationDeliveryMethodsAreAsynchronous() throws NoSuchMethodException {
        Async emailAsync = VerificationNotificationService.class
                .getMethod("sendEmail", String.class, String.class, String.class)
                .getAnnotation(Async.class);
        Async bookingAsync = VerificationNotificationService.class
                .getMethod("sendBookingNotification", Booking.class)
                .getAnnotation(Async.class);
        Async whatsappAsync = VerificationNotificationService.class
                .getMethod("sendWhatsApp", String.class, String.class)
                .getAnnotation(Async.class);

        assertEquals("notificationTaskExecutor", emailAsync.value());
        assertEquals("notificationTaskExecutor", bookingAsync.value());
        assertEquals("notificationTaskExecutor", whatsappAsync.value());
    }

    @Test
    void blankFromAddressDoesNotCreateMalformedMail() {
        ReflectionTestUtils.setField(service, "fromEmail", " ");

        service.sendEmail("person@example.com", "123456", "email verification");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertNull(captor.getValue().getFrom());
        assertArrayEquals(new String[]{"person@example.com"}, captor.getValue().getTo());
    }

    @Test
    void sendBookingNotificationSendsEmailToOwner() {
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@ramellian.com");

        User owner = new User();
        owner.setFirstName("Alice");
        owner.setEmailAddress("owner@example.com");

        Vehicle vehicle = new Vehicle();
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

        service.sendBookingNotification(booking);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertEquals("no-reply@ramellian.com", sent.getFrom());
        assertArrayEquals(new String[]{"owner@example.com"}, sent.getTo());
        assertTrue(sent.getSubject().contains("Toyota Prado"));
        assertTrue(sent.getSubject().contains("KDD 123A"));
        assertTrue(sent.getText().contains("Alice"));
        assertTrue(sent.getText().contains("Bob Smith"));
        assertTrue(sent.getText().contains(booking.getId().toString()));
        assertTrue(sent.getText().contains("SELF_DRIVE"));
        assertTrue(sent.getText().contains("AIRPORT_TRANSFER"));
        assertTrue(sent.getText().contains("Early pickup preferred"));
    }

    @Test
    void sendBookingNotificationSkipsWhenOwnerEmailMissing() {
        Booking booking = new Booking();
        Vehicle vehicle = new Vehicle();
        booking.setVehicle(vehicle);

        service.sendBookingNotification(booking);
        verifyNoInteractions(mailSender);

        User owner = new User();
        owner.setEmailAddress("");
        vehicle.setOwner(owner);
        service.sendBookingNotification(booking);
        verifyNoInteractions(mailSender);
    }
}
