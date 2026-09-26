package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.DocumentReviewRequest;
import com.ramellian_executive.travels.repository.UserDocumentRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserDocumentRepository documentRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock NotificationService appNotificationService;
    @Mock VerificationNotificationService emailNotificationService;
    @Mock AuditLogService auditLogService;
    @Mock Authentication authentication;

    @Test
    void storesRenterDrivingLicenseAsPending(@TempDir Path directory) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("renter@example.com");
        when(authentication.getName()).thenReturn(user.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(user.getEmailAddress())).thenReturn(Optional.of(user));
        when(documentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentService service = new DocumentService(userRepository, documentRepository,
                vehicleRepository, appNotificationService, emailNotificationService, auditLogService, directory.toString());
        var response = service.upload("DRIVING_LICENSE", null,
                new MockMultipartFile("file", "license.pdf", "application/pdf", "license".getBytes()),
                authentication);

        assertEquals("DRIVING_LICENSE", response.documentType());
        assertEquals("PENDING", response.reviewStatus());
        verify(documentRepository).save(any(UserDocument.class));
        verify(auditLogService).logAction(eq(authentication), eq("DOCUMENT_UPLOAD"), eq("DOCUMENT"), any(), anyString(), eq("SUCCESS"));
    }

    @Test
    void reviewApprovesDocumentAndSendsNotifications(@TempDir Path directory) {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmailAddress("owner@example.com");
        owner.setFirstName("John");

        UserDocument document = new UserDocument();
        UUID docId = UUID.randomUUID();
        document.setId(docId);
        document.setUser(owner);
        document.setDocumentType(DocumentType.NATIONAL_ID);
        document.setReviewStatus(DocumentReviewStatus.PENDING);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentService service = new DocumentService(userRepository, documentRepository,
                vehicleRepository, appNotificationService, emailNotificationService, auditLogService, directory.toString());

        DocumentReviewRequest request = new DocumentReviewRequest("APPROVED", "Looks good");
        var response = service.review(docId, request, authentication);

        assertEquals("APPROVED", response.reviewStatus());
        assertEquals("Looks good", response.reviewNotes());

        verify(appNotificationService).createNotification(
                eq(owner),
                eq("Document Approved"),
                contains("NATIONAL_ID"),
                eq("DOCUMENT_REVIEW"),
                eq(docId),
                eq("DOCUMENT")
        );
        verify(emailNotificationService).sendDocumentReviewNotification(any(UserDocument.class));
        verify(auditLogService).logAction(eq(authentication), eq("DOCUMENT_REVIEW_APPROVED"), eq("DOCUMENT"), eq(docId.toString()), anyString(), eq("SUCCESS"));
    }

    @Test
    void reviewRejectsDocumentAndSendsNotifications(@TempDir Path directory) {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmailAddress("owner@example.com");
        owner.setFirstName("Jane");

        UserDocument document = new UserDocument();
        UUID docId = UUID.randomUUID();
        document.setId(docId);
        document.setUser(owner);
        document.setDocumentType(DocumentType.DRIVING_LICENSE);
        document.setReviewStatus(DocumentReviewStatus.PENDING);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentService service = new DocumentService(userRepository, documentRepository,
                vehicleRepository, appNotificationService, emailNotificationService, auditLogService, directory.toString());

        DocumentReviewRequest request = new DocumentReviewRequest("REJECTED", "Image is blurry");
        var response = service.review(docId, request, authentication);

        assertEquals("REJECTED", response.reviewStatus());
        assertEquals("Image is blurry", response.reviewNotes());

        verify(appNotificationService).createNotification(
                eq(owner),
                eq("Document Rejected"),
                contains("Image is blurry"),
                eq("DOCUMENT_REVIEW"),
                eq(docId),
                eq("DOCUMENT")
        );
        verify(emailNotificationService).sendDocumentReviewNotification(any(UserDocument.class));
        verify(auditLogService).logAction(eq(authentication), eq("DOCUMENT_REVIEW_REJECTED"), eq("DOCUMENT"), eq(docId.toString()), anyString(), eq("SUCCESS"));
    }
}
