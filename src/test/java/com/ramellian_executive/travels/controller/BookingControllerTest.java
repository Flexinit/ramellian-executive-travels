package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock BookingService bookingService;
    @InjectMocks BookingController controller;

    private final Authentication auth = mock(Authentication.class);

    @Test
    void create_delegatesToService() {
        BookingRequest req = new BookingRequest(UUID.randomUUID(),
                ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(3));
        BookingResponse expected = mock(BookingResponse.class);
        when(bookingService.create(req, auth)).thenReturn(expected);

        BookingResponse actual = controller.create(req, auth);

        assertEquals(expected, actual);
        verify(bookingService).create(req, auth);
    }

    @Test
    void mine_delegatesToService() {
        when(bookingService.mine(auth)).thenReturn(List.of());

        assertEquals(0, controller.mine(auth).size());
        verify(bookingService).mine(auth);
    }

    @Test
    void get_delegatesToService() {
        UUID id = UUID.randomUUID();
        BookingResponse expected = mock(BookingResponse.class);
        when(bookingService.get(id, auth)).thenReturn(expected);

        assertEquals(expected, controller.get(id, auth));
        verify(bookingService).get(id, auth);
    }

    @Test
    void forOwner_delegatesToService() {
        when(bookingService.forOwner(auth)).thenReturn(List.of());

        assertEquals(0, controller.forOwner(auth).size());
        verify(bookingService).forOwner(auth);
    }

    @Test
    void renterReport_delegatesToService() {
        BookingReportResponse expected = mock(BookingReportResponse.class);
        when(bookingService.renterReport(auth)).thenReturn(expected);

        assertEquals(expected, controller.renterReport(auth));
        verify(bookingService).renterReport(auth);
    }

    @Test
    void ownerReport_delegatesToService() {
        BookingReportResponse expected = mock(BookingReportResponse.class);
        when(bookingService.ownerReport(auth)).thenReturn(expected);

        assertEquals(expected, controller.ownerReport(auth));
        verify(bookingService).ownerReport(auth);
    }

    @Test
    void platformReport_delegatesToService() {
        BookingReportResponse expected = mock(BookingReportResponse.class);
        when(bookingService.platformReport()).thenReturn(expected);

        assertEquals(expected, controller.platformReport());
        verify(bookingService).platformReport();
    }

    @Test
    void changeStatus_delegatesToService() {
        UUID id = UUID.randomUUID();
        BookingStatusRequest req = new BookingStatusRequest("CONFIRMED", null);
        BookingResponse expected = mock(BookingResponse.class);
        when(bookingService.changeStatus(id, req, auth)).thenReturn(expected);

        assertEquals(expected, controller.changeStatus(id, req, auth));
        verify(bookingService).changeStatus(id, req, auth);
    }

    @Test
    void assignChauffeur_delegatesToService() {
        UUID bookingId = UUID.randomUUID();
        UUID chauffeurId = UUID.randomUUID();
        ChauffeurAssignmentRequest req = new ChauffeurAssignmentRequest(chauffeurId);
        BookingResponse expected = mock(BookingResponse.class);
        when(bookingService.assignChauffeur(bookingId, chauffeurId, auth)).thenReturn(expected);

        assertEquals(expected, controller.assignChauffeur(bookingId, req, auth));
        verify(bookingService).assignChauffeur(bookingId, chauffeurId, auth);
    }

    @Test
    void createBatch_delegatesToService_returns201() {
        BatchBookingRequest req = mock(BatchBookingRequest.class);
        BookingGroupResponse expected = mock(BookingGroupResponse.class);
        when(bookingService.createBatch(req, auth)).thenReturn(expected);

        ResponseEntity<BookingGroupResponse> resp = controller.createBatch(req, auth);

        assertEquals(201, resp.getStatusCode().value());
        assertEquals(expected, resp.getBody());
        verify(bookingService).createBatch(req, auth);
    }

    @Test
    void quote_delegatesToService_returns200() {
        BookingQuoteRequest req = mock(BookingQuoteRequest.class);
        BookingQuoteResponse expected = mock(BookingQuoteResponse.class);
        when(bookingService.quote(req)).thenReturn(expected);

        ResponseEntity<BookingQuoteResponse> resp = controller.quote(req);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(expected, resp.getBody());
        verify(bookingService).quote(req);
    }

    @Test
    void recordReturn_delegatesToService_returns200() {
        UUID id = UUID.randomUUID();
        BookingReturnRequest req = new BookingReturnRequest(ZonedDateTime.now().plusDays(3), null);
        BookingResponse expected = mock(BookingResponse.class);
        when(bookingService.recordReturn(id, req, auth)).thenReturn(expected);

        ResponseEntity<BookingResponse> resp = controller.recordReturn(id, req, auth);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(expected, resp.getBody());
        verify(bookingService).recordReturn(id, req, auth);
    }
}
