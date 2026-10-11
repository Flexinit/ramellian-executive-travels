package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.entity.Payment;
import com.ramellian_executive.travels.entity.PaymentChargeType;
import com.ramellian_executive.travels.entity.PaymentStatus;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.service.PaymentService;
import com.ramellian_executive.travels.service.ReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ReceiptService receiptService;

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

    @PostMapping(value = "/contracts/{contractId}/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractResponse sign(@PathVariable UUID contractId,
                                 @RequestPart MultipartFile signature,
                                 @RequestParam boolean consent,
                                 Authentication authentication,
                                 HttpServletRequest request) {
        return paymentService.signAgreement(contractId, signature, consent, authentication,
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

    @GetMapping("/bookings/{bookingId}/receipt")
    public ResponseEntity<Resource> receipt(@PathVariable UUID bookingId,
                                            @RequestParam(defaultValue = "false") boolean inline,
                                            Authentication authentication) {
        Payment payment = paymentRepository.findByBookingIdAndChargeType(bookingId, PaymentChargeType.RENTAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        boolean isRenter = payment.getBooking().getUser().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        boolean isOwner = payment.getBooking().getVehicle().getOwner().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        boolean isManager = authentication.getAuthorities().stream()
                .anyMatch(a -> "PAYMENT_MANAGE".equals(a.getAuthority())
                        || "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (!isRenter && !isOwner && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this receipt");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt is only available for successful payments");
        }

        Path path;
        if (isManager) {
            if (payment.getAdminReceiptFileName() == null) {
                payment = receiptService.generateAdminReceipt(payment);
            }
            path = receiptService.adminReceiptPath(payment);
        } else {
            if (payment.getReceiptFileName() == null) {
                payment = receiptService.generate(payment);
            }
            path = receiptService.receiptPath(payment);
        }

        if (path == null || !path.toFile().exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt file not found");
        }

        String vehicleTitle = com.ramellian_executive.travels.service.EmailTemplateService
                .vehicleTitle(payment.getBooking().getVehicle())
                .replaceAll("[^a-zA-Z0-9]", "_");

        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename("Receipt-" + vehicleTitle + ".pdf").build()
                : ContentDisposition.attachment().filename("Receipt-" + vehicleTitle + ".pdf").build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(path));
    }
}
