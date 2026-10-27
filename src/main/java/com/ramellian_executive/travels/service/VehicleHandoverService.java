package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.HandoverResponse;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.repository.VehicleHandoverRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleHandoverService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final VehicleHandoverRepository handoverRepository;
    private final DisbursementService disbursementService;
    private final OverstayChargeService overstayChargeService;
    private final Path handoverDirectory;

    public VehicleHandoverService(BookingRepository bookingRepository,
                                  PaymentRepository paymentRepository,
                                  VehicleHandoverRepository handoverRepository,
                                  DisbursementService disbursementService,
                                  OverstayChargeService overstayChargeService,
                                  @Value("${app.handover.directory:uploads/handovers}") String handoverDirectory) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.handoverRepository = handoverRepository;
        this.disbursementService = disbursementService;
        this.overstayChargeService = overstayChargeService;
        this.handoverDirectory = Paths.get(handoverDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public HandoverResponse pickup(UUID bookingId, MultipartFile signature,
                                   Integer odometerReading, FuelLevel fuelLevel,
                                   String conditionNotes, boolean damageNoted,
                                   String damageDescription, String location,
                                   Authentication authentication, String ipAddress, String userAgent) {
        Booking booking = findBooking(bookingId);
        requireRenter(booking, authentication);

        if (booking.getStatus() == BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment must be completed before collecting the vehicle");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicle cannot be collected in the current booking state");
        }
        if (!paymentRepository.existsByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESSFUL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment must be completed before collecting the vehicle");
        }
        if (handoverRepository.existsByBookingIdAndType(bookingId, HandoverType.PICKUP)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pickup has already been recorded for this booking");
        }
        if (damageNoted && (damageDescription == null || damageDescription.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Damage description is required when damage is noted");
        }

        VehicleHandover handover = buildHandover(booking, HandoverType.PICKUP, odometerReading,
                fuelLevel, conditionNotes, damageNoted, damageDescription, location,
                authentication.getName(), ipAddress, userAgent);
        VehicleHandover saved = handoverRepository.save(handover);
        saveSignature(signature, saved, HandoverType.PICKUP);

        booking.setStatus(BookingStatus.ACTIVE);
        bookingRepository.save(booking);

        disbursementService.createForPickup(bookingId);

        return HandoverResponse.from(saved);
    }

    @Transactional
    public HandoverResponse dropoff(UUID bookingId, MultipartFile signature,
                                    Integer odometerReading, FuelLevel fuelLevel,
                                    String conditionNotes, boolean damageNoted,
                                    String damageDescription, String location,
                                    Authentication authentication, String ipAddress, String userAgent) {
        Booking booking = findBooking(bookingId);
        requireRenter(booking, authentication);

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicle can only be returned once the rental is active");
        }
        if (!handoverRepository.existsByBookingIdAndType(bookingId, HandoverType.PICKUP)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pickup must be recorded before drop-off");
        }
        if (handoverRepository.existsByBookingIdAndType(bookingId, HandoverType.DROPOFF)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Drop-off has already been recorded for this booking");
        }
        if (damageNoted && (damageDescription == null || damageDescription.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Damage description is required when damage is noted");
        }

        VehicleHandover handover = buildHandover(booking, HandoverType.DROPOFF, odometerReading,
                fuelLevel, conditionNotes, damageNoted, damageDescription, location,
                authentication.getName(), ipAddress, userAgent);
        VehicleHandover saved = handoverRepository.save(handover);
        saveSignature(signature, saved, HandoverType.DROPOFF);

        booking.setActualEndTime(ZonedDateTime.now());
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        overstayChargeService.createIfOverstay(booking);

        return HandoverResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<HandoverResponse> getByBooking(UUID bookingId, Authentication authentication) {
        Booking booking = findBooking(bookingId);
        boolean renter = booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean owner = booking.getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        if (!renter && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access these handover records");
        }
        return handoverRepository.findByBookingIdOrderByHandoverTimeAsc(bookingId)
                .stream().map(HandoverResponse::from).toList();
    }

    private VehicleHandover buildHandover(Booking booking, HandoverType type,
                                          Integer odometerReading, FuelLevel fuelLevel,
                                          String conditionNotes, boolean damageNoted,
                                          String damageDescription, String location,
                                          String signerEmail, String ipAddress, String userAgent) {
        VehicleHandover h = new VehicleHandover();
        h.setBooking(booking);
        h.setType(type);
        h.setHandoverTime(ZonedDateTime.now());
        h.setOdometerReading(odometerReading);
        h.setFuelLevel(fuelLevel);
        h.setConditionNotes(conditionNotes);
        h.setDamageNoted(damageNoted);
        h.setDamageDescription(damageDescription);
        h.setLocation(location);
        h.setSignedBy(signerEmail);
        h.setSignedAt(ZonedDateTime.now());
        h.setSignerIpAddress(ipAddress);
        h.setSignerUserAgent(userAgent);
        return h;
    }

    private void saveSignature(MultipartFile signature, VehicleHandover handover, HandoverType type) {
        if (signature == null || signature.isEmpty()) return;
        if (signature.getContentType() == null || !signature.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature must be an image file");
        }
        String ext = resolveExtension(signature.getContentType());
        String fileName = handover.getId() + "-" + type.name().toLowerCase() + "-signature." + ext;
        try {
            Files.createDirectories(handoverDirectory);
            Files.copy(signature.getInputStream(), handoverDirectory.resolve(fileName));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store handover signature", ex);
        }
        handover.setSignatureFileName(fileName);
        handoverRepository.save(handover);
    }

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private Booking findBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void requireRenter(Booking booking, Authentication authentication) {
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the renter can submit handover details");
        }
    }
}
