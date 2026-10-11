package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.PaymentCheckoutResponse;
import com.ramellian_executive.travels.model.PaymentStatusResponse;
import com.ramellian_executive.travels.service.PesapalPaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PesapalPaymentControllerTest {

    @Mock PesapalPaymentService service;
    @InjectMocks PesapalPaymentController controller;

    private final Authentication auth = mock(Authentication.class);

    @Test
    void checkout_delegatesToService() {
        UUID bookingId = UUID.randomUUID();
        PaymentCheckoutResponse expected = new PaymentCheckoutResponse(
                UUID.randomUUID(), "PESAPAL", "TRACK-001", "https://redirect", "PENDING");
        when(service.checkout(bookingId, auth)).thenReturn(expected);

        PaymentCheckoutResponse actual = controller.checkout(bookingId, auth);

        assertEquals(expected, actual);
        verify(service).checkout(bookingId, auth);
    }

    @Test
    void status_delegatesToService() {
        UUID bookingId = UUID.randomUUID();
        PaymentStatusResponse expected = new PaymentStatusResponse(
                UUID.randomUUID(), bookingId, "PESAPAL", "SUCCESSFUL",
                5000.0, 4850.0, 0.0, 0.0, 75.0, 97.0, 4753.0,
                "TRACK-001", "BOOKING-ref-001", "REC-123");
        when(service.status(bookingId, auth)).thenReturn(expected);

        PaymentStatusResponse actual = controller.status(bookingId, auth);

        assertEquals(expected, actual);
        verify(service).status(bookingId, auth);
    }

    @Test
    void ipn_delegatesToServiceAndReturnsReceived() {
        Map<String, String> response = controller.ipn("TRACK-001", "BOOKING-ref-uuid");

        assertEquals("received", response.get("status"));
        verify(service).processCallback("TRACK-001", "BOOKING-ref-uuid");
    }

    @Test
    void ipn_servicExceptionDoesNotPropagate() {
        doThrow(new RuntimeException("Pesapal error"))
                .when(service).processCallback(anyString(), anyString());

        Map<String, String> response = controller.ipn("TRACK-001", "BAD-REF");

        assertEquals("received", response.get("status"));
    }

    @Test
    void checkoutGroup_delegatesToService() {
        UUID groupId = UUID.randomUUID();
        PaymentCheckoutResponse expected = new PaymentCheckoutResponse(
                UUID.randomUUID(), "PESAPAL", "GROUP-TRACK", "https://redirect", "PENDING");
        when(service.checkoutGroup(groupId, auth)).thenReturn(expected);

        ResponseEntity<PaymentCheckoutResponse> response = controller.checkoutGroup(groupId, auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(service).checkoutGroup(groupId, auth);
    }

    @Test
    void checkoutOverstay_delegatesToService() {
        UUID bookingId = UUID.randomUUID();
        PaymentCheckoutResponse expected = new PaymentCheckoutResponse(
                UUID.randomUUID(), "PESAPAL", "OVER-TRACK", "https://redirect", "PENDING");
        when(service.checkoutOverstay(bookingId, auth)).thenReturn(expected);

        ResponseEntity<PaymentCheckoutResponse> response = controller.checkoutOverstay(bookingId, auth);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(service).checkoutOverstay(bookingId, auth);
    }
}
