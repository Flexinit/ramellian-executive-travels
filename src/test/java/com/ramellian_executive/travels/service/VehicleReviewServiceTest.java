package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.ReviewRequest;
import com.ramellian_executive.travels.model.ReviewResponse;
import com.ramellian_executive.travels.repository.ReviewRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.service.vehicle.VehicleReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock UserRepository userRepository;
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
        when(reviewRepository.existsByBookingAndReviewType(eq(booking), any())).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.averageRatingByVehicle(vehicleId)).thenReturn(5.0);

        ReviewResponse response = service.create(booking, new ReviewRequest(5, true, "Great", null), authentication);

        assertEquals(5, response.rating());
        verify(vehicleRepository).save(vehicle);
        assertEquals(5.0, vehicle.getRating());
    }

    @Test
    void findsReviewsByOwner() {
        UUID ownerId = UUID.randomUUID();
        when(userRepository.existsById(ownerId)).thenReturn(true);
        Review review = new Review();
        review.setId(UUID.randomUUID());
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        booking.setVehicle(vehicle);
        User renter = new User();
        renter.setId(UUID.randomUUID());
        renter.setFirstName("John");
        renter.setLastName("Doe");
        booking.setUser(renter);
        review.setBooking(booking);
        review.setRating(4);
        review.setFeedback("Awesome car");
        when(reviewRepository.findAllByVehicleOwnerId(ownerId)).thenReturn(java.util.List.of(review));

        java.util.List<ReviewResponse> result = service.findByOwner(ownerId);

        assertEquals(1, result.size());
        assertEquals("Awesome car", result.get(0).feedback());
    }
}
