package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.repository.UserDocumentRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserDocumentRepository documentRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock Authentication authentication;

    @Test
    void storesRenterDrivingLicenseAsPending(@TempDir Path directory) {
        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setEmailAddress("renter@example.com");
        when(authentication.getName()).thenReturn(user.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(user.getEmailAddress())).thenReturn(Optional.of(user));
        when(documentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentService service = new DocumentService(userRepository, documentRepository,
                vehicleRepository, directory.toString());
        var response = service.upload("DRIVING_LICENSE", null,
                new MockMultipartFile("file", "license.pdf", "application/pdf", "license".getBytes()),
                authentication);

        assertEquals("DRIVING_LICENSE", response.documentType());
        assertEquals("PENDING", response.reviewStatus());
        verify(documentRepository).save(any(UserDocument.class));
    }
}
