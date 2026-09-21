package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.service.ContractTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contract-templates")
public class ContractTemplateController {
    private final ContractTemplateService service;

    @PostMapping("/owner")
    public ContractTemplateResponse submitOwner(@Valid @RequestBody ContractTemplateRequest request,
                                                Authentication authentication) {
        return service.submitOwner(request, authentication);
    }

    @GetMapping("/mine")
    public List<ContractTemplateResponse> mine(Authentication authentication) {
        return service.mine(authentication);
    }

    @PostMapping("/admin")
    public ContractTemplateResponse createPlatform(@Valid @RequestBody ContractTemplateRequest request) {
        return service.createPlatform(request);
    }

    @GetMapping("/admin")
    public List<ContractTemplateResponse> all() {
        return service.all();
    }

    @PutMapping("/admin/{id}/review")
    public ContractTemplateResponse review(@PathVariable UUID id,
                                           @Valid @RequestBody ContractTemplateReviewRequest request) {
        return service.review(id, request);
    }
}
