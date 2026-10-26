package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.DisbursementResponse;
import com.ramellian_executive.travels.service.DisbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/owner/disbursements")
public class OwnerDisbursementController {

    private final DisbursementService disbursementService;

    @GetMapping
    public List<DisbursementResponse> myPayouts(Authentication authentication) {
        return disbursementService.listForOwner(authentication);
    }
}
