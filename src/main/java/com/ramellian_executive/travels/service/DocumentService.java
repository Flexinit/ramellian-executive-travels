package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.*;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class DocumentService {
    private final UserRepository userRepository;
    private final UserDocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;
    private final Path directory;

    public DocumentService(UserRepository userRepository, UserDocumentRepository documentRepository,
                           VehicleRepository vehicleRepository,
                           @Value("${app.document.directory:uploads/documents}") String directory) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
        this.directory = Paths.get(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public DocumentResponse upload(String type, UUID vehicleId, MultipartFile file,
                                   Authentication authentication) {
        User user = findUser(authentication.getName());
        DocumentType documentType;
        try {
            documentType = DocumentType.valueOf(normalize(type));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document type");
        }
        validateType(documentType, vehicleId, user);
        if (file.isEmpty() || file.getContentType() == null
                || (!file.getContentType().startsWith("image/") && !file.getContentType().equals("application/pdf"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image or PDF documents are accepted");
        }
        String fileName = UUID.randomUUID() + extension(file.getOriginalFilename());
        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), directory.resolve(fileName));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Document could not be stored", ex);
        }
        UserDocument document = new UserDocument();
        document.setUser(user);
        document.setVehicle(vehicleId == null ? null : vehicleRepository.findById(vehicleId).orElseThrow());
        document.setDocumentType(documentType);
        document.setFileName(fileName);
        document.setOriginalFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> currentUserDocuments(Authentication authentication) {
        return documentRepository.findAllByUserOrderByCreatedAtDesc(findUser(authentication.getName()))
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> pendingDocuments() {
        return documentRepository.findAllByReviewStatusOrderByCreatedAtAsc(DocumentReviewStatus.PENDING)
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional
    public DocumentResponse review(UUID id, DocumentReviewRequest request) {
        UserDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        String status = normalize(request.status());
        DocumentReviewStatus reviewStatus;
        try {
            reviewStatus = DocumentReviewStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status must be APPROVED or REJECTED");
        }
        if (reviewStatus == DocumentReviewStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be APPROVED or REJECTED");
        }
        document.setReviewStatus(reviewStatus);
        document.setReviewNotes(request.notes());
        return DocumentResponse.from(documentRepository.save(document));
    }

    public Resource download(UUID id, Authentication authentication) {
        UserDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        User user = findUser(authentication.getName());
        boolean owner = document.getUser().getId().equals(user.getId());
        boolean reviewer = authentication.getAuthorities().stream()
                .anyMatch(authority -> "DOCUMENT_REVIEW".equals(authority.getAuthority()));
        if (!owner && !reviewer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this document");
        }
        try {
            Resource resource = new UrlResource(directory.resolve(document.getFileName()).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found");
            }
            return resource;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file not found", ex);
        }
    }

    private void validateType(DocumentType type, UUID vehicleId, User user) {
        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
            if (vehicle.getOwner() == null || !vehicle.getOwner().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this vehicle");
            }
            if (type != DocumentType.INSURANCE_STICKER
                    && type != DocumentType.VEHICLE_REGISTRATION) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vehicle document type");
            }
        } else if (type == DocumentType.INSURANCE_STICKER
                || type == DocumentType.VEHICLE_REGISTRATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user document type");
        } else if ((type == DocumentType.BUSINESS_REGISTRATION
                || type == DocumentType.TAX_CERTIFICATE)
                && !"BUSINESS".equalsIgnoreCase(user.getOwnerType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Business documents require a BUSINESS owner profile");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmailAddressIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String extension(String originalName) {
        if (originalName == null || !originalName.contains(".")) return "";
        return originalName.substring(originalName.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "");
    }
}
