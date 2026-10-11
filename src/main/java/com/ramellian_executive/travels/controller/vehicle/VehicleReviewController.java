package com.ramellian_executive.travels.controller.vehicle;
import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.service.vehicle.VehicleReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
public class VehicleReviewController {

    private final VehicleReviewService service;
    private final BookingRepository bookingRepository;

    @PostMapping("/bookings/{bookingId}")
    public ReviewResponse create(@PathVariable UUID bookingId, @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        Booking booking = findBooking(bookingId);
        return service.create(booking, request, authentication);
    }

    @PostMapping("/bookings/{bookingId}/renter")
    public ReviewResponse rateRenter(@PathVariable UUID bookingId, @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        Booking booking = findBooking(bookingId);
        return service.createRenterReview(booking, request, authentication);
    }

    @GetMapping("/vehicles/{vehicleId}")
    public List<ReviewResponse> findByVehicle(@PathVariable UUID vehicleId) {
        return service.findByVehicle(vehicleId);
    }

    @GetMapping("/owners/{ownerId}")
    public List<ReviewResponse> findByOwner(@PathVariable UUID ownerId) {
        return service.findByOwner(ownerId);
    }

    @GetMapping("/renters/{renterId}")
    public List<ReviewResponse> findRenterReviews(@PathVariable UUID renterId) {
        return service.findRenterReviews(renterId);
    }

    private Booking findBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }
}
