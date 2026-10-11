package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.PlatformSettingsRequest;
import com.ramellian_executive.travels.model.PlatformSettingsResponse;
import com.ramellian_executive.travels.service.PlatformSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform-settings")
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping("/commission")
    public ResponseEntity<PlatformSettingsResponse> get() {
        return ResponseEntity.ok(platformSettingsService.get());
    }

    @PutMapping("/commission")
    public ResponseEntity<PlatformSettingsResponse> set(@Valid @RequestBody PlatformSettingsRequest request) {
        return ResponseEntity.ok(platformSettingsService.set(request));
    }
}
