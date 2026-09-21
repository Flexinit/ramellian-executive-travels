package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.service.vehicle.VehiclePhotoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehiclePhotoServiceTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock Authentication authentication;

    @Test
    void storesUploadedPhotoForVehicleOwner() throws Exception {
        Path directory = Files.createTempDirectory("vehicle-photos-test");
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(authentication.getName()).thenReturn("owner@example.com");
        com.ramellian_executive.travels.entity.User owner = new com.ramellian_executive.travels.entity.User();
        owner.setEmailAddress("owner@example.com");
        vehicle.setOwner(owner);

        VehiclePhotoService service = new VehiclePhotoService(vehicleRepository, directory.toString());
        var response = service.upload(vehicle.getId(),
                new MockMultipartFile("file", "car.jpg", "image/jpeg", "image".getBytes()), authentication);

        assertNotNull(response.url());
        assertNotNull(response.fileName());
        assertTrue(Files.exists(directory.resolve(response.fileName())));
    }
}
