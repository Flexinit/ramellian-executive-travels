package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.OwnerStatementResponse;
import com.ramellian_executive.travels.service.OwnerStatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owner/statements")
public class OwnerStatementController {

    private final OwnerStatementService statementService;

    @GetMapping
    public OwnerStatementResponse statement(
            Authentication authentication,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to) {
        return statementService.statement(authentication, vehicleId, from, to);
    }
}
