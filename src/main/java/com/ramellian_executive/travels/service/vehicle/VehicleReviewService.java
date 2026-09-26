package com.ramellian_executive.travels.service.vehicle;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.*;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleReviewService {
    private final ReviewRepository reviewRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReviewResponse create(Booking booking, ReviewRequest request, Authentication authentication) {
        if (booking.getStatus() != BookingStatus.COMPLETED
                || !booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the renter can review a completed booking");
        }
        if (reviewRepository.existsByBooking(booking)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has already been reviewed");
        }
        Review review = new Review();
        review.setBooking(booking);
        review.setRating(request.rating());
        review.setFeedback(request.feedback());
        review.setComplaint(request.complaint());
        Review saved = reviewRepository.save(review);
        booking.getVehicle().setRating(reviewRepository.averageRatingByVehicle(booking.getVehicle().getId()));
        vehicleRepository.save(booking.getVehicle());
        return ReviewResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByVehicle(UUID vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found");
        }
        return reviewRepository.findAllByVehicleId(vehicleId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByOwner(UUID ownerId) {
        if (!userRepository.existsById(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found");
        }
        return reviewRepository.findAllByVehicleOwnerId(ownerId).stream()
                .map(ReviewResponse::from)
                .toList();
    }
}
