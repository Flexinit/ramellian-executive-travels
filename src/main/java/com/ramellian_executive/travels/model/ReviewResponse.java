package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Review;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID vehicleId, String reviewerEmail, Double rating,
                             String feedback, String complaint) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getBooking().getVehicle().getId(),
                review.getBooking().getUser().getEmailAddress(), review.getRating(),
                review.getFeedback(), review.getComplaint());
    }
}
