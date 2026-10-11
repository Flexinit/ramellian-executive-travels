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
        if ((booking.getStatus() != BookingStatus.COMPLETED && booking.getStatus() != BookingStatus.ACTIVE)
                || !booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the renter can review an active or completed booking");
        }
        if (reviewRepository.existsByBookingAndReviewType(booking, ReviewType.VEHICLE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking has already been reviewed");
        }
        Review review = buildReview(booking, request, ReviewType.VEHICLE);
        Review saved = reviewRepository.save(review);
        updateVehicleRating(booking);
        return ReviewResponse.from(saved);
    }

    @Transactional
    public ReviewResponse createRenterReview(Booking booking, ReviewRequest request, Authentication authentication) {
        boolean isOwner = booking.getVehicle().getOwner().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "BOOKING_MANAGE".equals(a.getAuthority())
                        || "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (!isOwner && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the vehicle owner can rate the renter");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED && booking.getStatus() != BookingStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Renter can only be rated on an active or completed booking");
        }
        if (reviewRepository.existsByBookingAndReviewType(booking, ReviewType.RENTER)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Renter has already been rated for this booking");
        }
        Review review = buildReview(booking, request, ReviewType.RENTER);
        return ReviewResponse.from(reviewRepository.save(review));
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

    @Transactional(readOnly = true)
    public List<ReviewResponse> findRenterReviews(UUID renterId) {
        if (!userRepository.existsById(renterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return reviewRepository.findAllRenterReviewsByRenterId(renterId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    private Review buildReview(Booking booking, ReviewRequest request, ReviewType type) {
        Review review = new Review();
        review.setBooking(booking);
        review.setReviewType(type);
        review.setRating(request.rating());
        review.setCustomerSatisfied(request.customerSatisfied());
        review.setFeedback(request.feedback());
        review.setComplaint(request.complaint());
        return review;
    }

    private void updateVehicleRating(Booking booking) {
        booking.getVehicle().setRating(
                reviewRepository.averageRatingByVehicle(booking.getVehicle().getId()));
        vehicleRepository.save(booking.getVehicle());
    }
}
