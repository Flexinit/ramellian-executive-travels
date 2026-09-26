package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.AuditLogResponse;
import com.ramellian_executive.travels.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditLogController controller;

    @Test
    void getAuditLogsReturnsOk() {
        UUID id = UUID.randomUUID();
        AuditLogResponse item = new AuditLogResponse(
                id, "admin@ramellian.com", UUID.randomUUID(), "ROLE_ADMIN",
                "DOCUMENT_REVIEW_APPROVED", "DOCUMENT", "doc-1",
                "Approved ID", "127.0.0.1", "SUCCESS", ZonedDateTime.now()
        );
        Page<AuditLogResponse> page = new PageImpl<>(List.of(item));

        when(auditLogService.getAuditLogs(eq("DOCUMENT_REVIEW_APPROVED"), eq("admin@ramellian.com"), eq("DOCUMENT"), any()))
                .thenReturn(page);

        ResponseEntity<Page<AuditLogResponse>> response = controller.getAuditLogs(
                "DOCUMENT_REVIEW_APPROVED", "admin@ramellian.com", "DOCUMENT", PageRequest.of(0, 10));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("DOCUMENT_REVIEW_APPROVED", response.getBody().getContent().get(0).action());
    }

    @Test
    void getAuditLogByIdReturnsOk() {
        UUID id = UUID.randomUUID();
        AuditLogResponse item = new AuditLogResponse(
                id, "superadmin@ramellian.com", UUID.randomUUID(), "ROLE_SUPER_ADMIN",
                "AUTH_LOGIN", "USER", "user-1",
                "Login success", "127.0.0.1", "SUCCESS", ZonedDateTime.now()
        );

        when(auditLogService.getAuditLogById(id)).thenReturn(item);

        ResponseEntity<AuditLogResponse> response = controller.getAuditLogById(id);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(id, response.getBody().id());
        assertEquals("AUTH_LOGIN", response.getBody().action());
    }
}
