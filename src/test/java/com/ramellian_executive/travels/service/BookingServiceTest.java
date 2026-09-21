package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.BookingRequest;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository bookingRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
    @Mock Authentication authentication;

    @Test
    void rejectsOverlappingBooking() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setAvailable(true);
        User renter = new User();
        renter.setEmailAddress("renter@example.com");
        when(authentication.getName()).thenReturn(renter.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(renter.getEmailAddress())).thenReturn(Optional.of(renter));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(true);

        BookingService service = new BookingService(bookingRepository, vehicleRepository, userRepository, null);
        ZonedDateTime start = ZonedDateTime.now().plusDays(1);
        assertThrows(ResponseStatusException.class,
                () -> service.create(new BookingRequest(vehicleId, start, start.plusDays(2)), authentication));
        verify(bookingRepository, never()).save(any());
    }
}
