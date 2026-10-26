package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.ContactRequest;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import com.ramellian_executive.travels.service.PlatformSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contact")
public class ContactController {

    private final VerificationNotificationService notificationService;
    private final PlatformSettingsService platformSettingsService;

    @PostMapping
    public ResponseEntity<Void> submit(@Valid @RequestBody ContactRequest request) {
        String supportEmail = platformSettingsService.getSupportEmail();
        notificationService.sendContactFormToSupport(request, supportEmail);
        notificationService.sendContactAutoReply(request);
        return ResponseEntity.ok().build();
    }
}
