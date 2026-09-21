package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/bookings/{bookingId}/confirm")
    public ContractResponse confirm(@PathVariable UUID bookingId,
                                    @Valid @RequestBody PaymentConfirmationRequest request,
                                    Authentication authentication) {
        return paymentService.confirm(bookingId, request, authentication);
    }

    @PostMapping("/bookings/{bookingId}/contract/draft")
    public ContractResponse draft(@PathVariable UUID bookingId, Authentication authentication) {
        return paymentService.draft(bookingId, authentication);
    }

    @PostMapping("/bookings/{bookingId}/contract/accept")
    public ContractResponse accept(@PathVariable UUID bookingId,
                                   @Valid @RequestBody ContractAcceptanceRequest request,
                                   Authentication authentication) {
        return paymentService.accept(bookingId, request, authentication);
    }

    @PutMapping("/contracts/{contractId}/sign")
    public ContractResponse sign(@PathVariable UUID contractId,
                                 @RequestPart MultipartFile signature,
                                 @RequestPart boolean consent,
                                 Authentication authentication,
                                 HttpServletRequest request) {
        return paymentService.sign(contractId, signature, consent, authentication,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @GetMapping("/contracts/{contractId}")
    public ContractResponse getContract(@PathVariable UUID contractId,
                                        Authentication authentication) {
        return paymentService.getContract(contractId, authentication);
    }

    @GetMapping("/contracts/{contractId}/document")
    public ResponseEntity<Resource> document(@PathVariable UUID contractId,
                                             @RequestParam(defaultValue = "false") boolean signed,
                                             Authentication authentication) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .body(paymentService.download(contractId, signed, authentication));
    }
}
