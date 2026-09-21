package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.model.VehicleRequest;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.service.vehicle.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
    @Mock Authentication authentication;
    @InjectMocks
    VehicleService vehicleService;

    @Test
    void createVehicleAssignsAuthenticatedOwner() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        Vehicle saved = new Vehicle();
        saved.setModel("Toyota");
        saved.setOwner(owner);
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.createVehicle(
                new VehicleRequest("Toyota", "VIN123", "KDA123A", "Nairobi", 100.0,
                        5, "Black", "PETROL", "AUTOMATIC", true, false), authentication);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertSame(owner, captor.getValue().getOwner());
        assertEquals("Toyota", response.model());
    }

    @Test
    void getMissingVehicleReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> vehicleService.getVehicle(id));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void ownerCannotUpdateAnotherOwnersVehicle() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        User other = new User();
        other.setEmailAddress("other@example.com");
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(other);
        when(vehicleRepository.findById(any())).thenReturn(Optional.of(vehicle));
        when(authentication.getName()).thenReturn("owner@example.com");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> vehicleService.updateVehicle(UUID.randomUUID(), new VehicleRequest(
                        "Toyota", "VIN123", "KDA123A", "Nairobi", 100.0,
                        5, "Black", "PETROL", "AUTOMATIC", true, false), authentication));

        assertEquals(403, exception.getStatusCode().value());
    }
}
