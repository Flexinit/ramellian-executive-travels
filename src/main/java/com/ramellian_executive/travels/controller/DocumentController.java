package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping
    public DocumentResponse upload(@RequestParam String documentType,
                                   @RequestParam(required = false) UUID vehicleId,
                                   @RequestParam MultipartFile file,
                                   Authentication authentication) {
        return documentService.upload(documentType, vehicleId, file, authentication);
    }

    @GetMapping("/me")
    public List<DocumentResponse> mine(Authentication authentication) {
        return documentService.currentUserDocuments(authentication);
    }

    @GetMapping("/admin/pending")
    public List<DocumentResponse> pending() {
        return documentService.pendingDocuments();
    }

    @PutMapping("/admin/{id}/review")
    public DocumentResponse review(@PathVariable UUID id,
                                   @Valid @RequestBody DocumentReviewRequest request) {
        return documentService.review(id, request);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable UUID id,
                                             Authentication authentication) {
        Resource resource = documentService.download(id, authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
