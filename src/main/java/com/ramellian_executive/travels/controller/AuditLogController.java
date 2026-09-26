package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.AuditLogResponse;
import com.ramellian_executive.travels.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/audit-logs", "/api/v1/admin/audit-logs"})
@Tag(name = "System Trail & Audit Logs", description = "System trail logs for super admin and admin monitoring")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('AUDIT_READ') or hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Get system audit trail logs", description = "Retrieve paginated system activity and audit trail logs with optional filters")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String resourceType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(action, actorEmail, resourceType, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get audit log details by ID", description = "Retrieve a specific system trail log entry by its UUID")
    public ResponseEntity<AuditLogResponse> getAuditLogById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogService.getAuditLogById(id));
    }
}
