package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.AuditLog;
import com.ramellian_executive.travels.model.AuditLogResponse;
import com.ramellian_executive.travels.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void recordSavesAuditLogSuccessfully() {
        UUID actorId = UUID.randomUUID();
        auditLogService.record(
                "admin@ramellian.com",
                actorId,
                "ROLE_ADMIN",
                "DOCUMENT_REVIEW_APPROVED",
                "DOCUMENT",
                "12345",
                "Document approved",
                "127.0.0.1",
                "SUCCESS"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("admin@ramellian.com", saved.getActorEmail());
        assertEquals("DOCUMENT_REVIEW_APPROVED", saved.getAction());
        assertEquals("DOCUMENT", saved.getResourceType());
        assertEquals("12345", saved.getResourceId());
        assertEquals("SUCCESS", saved.getStatus());
    }

    @Test
    void logActionWithAuthenticationExtractsDetails() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("superadmin@ramellian.com");
        doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))).when(authentication).getAuthorities();

        auditLogService.logAction(
                authentication,
                "USER_STATUS_CHANGE",
                "USER",
                "user-id-1",
                "User activated",
                "SUCCESS"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("superadmin@ramellian.com", saved.getActorEmail());
        assertEquals("ROLE_SUPER_ADMIN", saved.getActorRole());
        assertEquals("USER_STATUS_CHANGE", saved.getAction());
    }

    @Test
    void getAuditLogsReturnsPageOfResponses() {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActorEmail("admin@ramellian.com");
        log.setAction("AUTH_LOGIN");

        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<AuditLogResponse> result = auditLogService.getAuditLogs("AUTH", "admin", "USER", PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("admin@ramellian.com", result.getContent().get(0).actorEmail());
    }

    @Test
    void getAuditLogByIdReturnsResponseWhenFound() {
        UUID id = UUID.randomUUID();
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setAction("TEST_ACTION");

        when(auditLogRepository.findById(id)).thenReturn(Optional.of(log));

        AuditLogResponse response = auditLogService.getAuditLogById(id);
        assertNotNull(response);
        assertEquals(id, response.id());
        assertEquals("TEST_ACTION", response.action());
    }
}
