package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Review;
import com.ramellian_executive.travels.entity.ReviewType;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID bookingId, UUID vehicleId, String reviewerEmail,
                             ReviewType reviewType, Integer rating, Boolean customerSatisfied,
                             String feedback, String complaint) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getBooking().getVehicle().getId(),
                review.getBooking().getUser().getEmailAddress(),
                review.getReviewType(),
                review.getRating(),
                review.getCustomerSatisfied(),
                review.getFeedback(),
                review.getComplaint());
    }
}
