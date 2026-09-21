package com.ramellian_executive.travels.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

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
        Async whatsappAsync = VerificationNotificationService.class
                .getMethod("sendWhatsApp", String.class, String.class)
                .getAnnotation(Async.class);

        assertEquals("notificationTaskExecutor", emailAsync.value());
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
}
