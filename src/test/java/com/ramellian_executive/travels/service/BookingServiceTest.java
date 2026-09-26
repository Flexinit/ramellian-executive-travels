package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.BookingRequest;
import com.ramellian_executive.travels.model.BookingResponse;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository bookingRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
    @Mock VerificationNotificationService notificationService;
    @Mock NotificationService appNotificationService;
    @Mock Authentication authentication;

    @Test
    void rejectsOverlappingBooking() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setAvailable(true);
        vehicle.setApprovalStatus(VehicleApprovalStatus.APPROVED);
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

    @Test
    void notifiesOwnerWhenBookingCreated() {
        UUID vehicleId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmailAddress("owner@example.com");
        owner.setFirstName("Owner");

        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setModel("Range Rover Sport");
        vehicle.setRegistrationNumber("KDC 789B");
        vehicle.setPricePerDay(150.0);
        vehicle.setAvailable(true);
        vehicle.setApprovalStatus(VehicleApprovalStatus.APPROVED);
        vehicle.setSelfDrive(true);
        vehicle.setOwner(owner);

        User renter = new User();
        renter.setId(UUID.randomUUID());
        renter.setEmailAddress("renter@example.com");
        renter.setFirstName("John");
        renter.setLastName("Doe");

        when(authentication.getName()).thenReturn(renter.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(renter.getEmailAddress())).thenReturn(Optional.of(renter));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.existsOverlappingBooking(any(), any(), any(), any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingService service = new BookingService(
                bookingRepository, vehicleRepository, userRepository, null, null, notificationService, appNotificationService);

        ZonedDateTime start = ZonedDateTime.now().plusDays(1);
        ZonedDateTime end = start.plusDays(3);
        BookingRequest request = new BookingRequest(vehicleId, start, end, "SELF_DRIVE", TripType.GENERAL, "Notes");

        BookingResponse response = service.create(request, authentication);

        assertNotNull(response);
        verify(notificationService).sendBookingNotification(any(Booking.class));
        verify(appNotificationService, times(2)).createNotification(any(), anyString(), anyString(), eq("BOOKING_CREATED"), any(), eq("BOOKING"));
    }
}
