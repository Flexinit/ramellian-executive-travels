package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.ReviewRequest;
import com.ramellian_executive.travels.model.ReviewResponse;
import com.ramellian_executive.travels.repository.ReviewRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.service.vehicle.VehicleReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock Authentication authentication;
    @InjectMocks
    VehicleReviewService service;

    @Test
    void createsReviewForCompletedBookingAndUpdatesVehicleRating() {
        UUID vehicleId = UUID.randomUUID();
        User renter = new User();
        renter.setEmailAddress("renter@example.com");
        Booking booking = new Booking();
        booking.setUser(renter);
        booking.setStatus("COMPLETED");
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setRating(4.0);
        booking.setVehicle(vehicle);
        when(authentication.getName()).thenReturn("renter@example.com");
        when(reviewRepository.existsByBooking(booking)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.averageRatingByVehicle(vehicleId)).thenReturn(5.0);

        ReviewResponse response = service.create(booking, new ReviewRequest(5.0, "Great", null), authentication);

        assertEquals(5.0, response.rating());
        verify(vehicleRepository).save(vehicle);
        assertEquals(5.0, vehicle.getRating());
    }
}
