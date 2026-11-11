package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.HandoverResponse;
import com.ramellian_executive.travels.repository.BookingContractRepository;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.PaymentRepository;
import com.ramellian_executive.travels.repository.VehicleHandoverRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
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
    private final BookingContractRepository contractRepository;
    private final DisbursementService disbursementService;
    private final OverstayChargeService overstayChargeService;
    private final Path handoverDirectory;

    public VehicleHandoverService(BookingRepository bookingRepository,
                                  PaymentRepository paymentRepository,
                                  VehicleHandoverRepository handoverRepository,
                                  BookingContractRepository contractRepository,
                                  DisbursementService disbursementService,
                                  OverstayChargeService overstayChargeService,
                                  @Value("${app.handover.directory:uploads/handovers}") String handoverDirectory) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.handoverRepository = handoverRepository;
        this.contractRepository = contractRepository;
        this.disbursementService = disbursementService;
        this.overstayChargeService = overstayChargeService;
        this.handoverDirectory = Paths.get(handoverDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public HandoverResponse pickup(UUID bookingId, MultipartFile signature,
                                   MultipartFile odometerPhoto,
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
        contractRepository.findByBookingId(bookingId).ifPresent(contract -> {
            if (contract.getStatus() != ContractStatus.FULLY_SIGNED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Both parties must sign the rental contract before the vehicle can be collected");
            }
        });
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
        saveOdometerPhoto(odometerPhoto, saved);

        booking.setStatus(BookingStatus.ACTIVE);
        bookingRepository.save(booking);

        disbursementService.createForPickup(bookingId);

        return HandoverResponse.from(saved);
    }

    @Transactional
    public HandoverResponse dropoff(UUID bookingId, MultipartFile signature,
                                    MultipartFile odometerPhoto,
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
        saveOdometerPhoto(odometerPhoto, saved);

        booking.setActualEndTime(ZonedDateTime.now());
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        overstayChargeService.createIfOverstay(booking);

        return HandoverResponse.from(saved);
    }

    /**
     * Records a vehicle return when the pickup step was never captured in the system.
     * Creates a backdated pickup (at the booking's scheduled start time) then records
     * the dropoff at the current time.  Accessible by the renter, the vehicle owner,
     * or any staff member with BOOKING_MANAGE authority.
     */
    @Transactional
    public List<HandoverResponse> recordReturn(UUID bookingId,
                                               MultipartFile dropoffSignature,
                                               MultipartFile dropoffOdometerPhoto,
                                               Integer odometerReading, FuelLevel fuelLevel,
                                               String conditionNotes, boolean damageNoted,
                                               String damageDescription, String location,
                                               Authentication authentication, String ipAddress, String userAgent) {
        Booking booking = findBooking(bookingId);
        requireRenterOrOwnerOrManager(booking, authentication);

        boolean isConfirmed = booking.getStatus() == BookingStatus.CONFIRMED;
        boolean isActive    = booking.getStatus() == BookingStatus.ACTIVE;

        if (!isConfirmed && !isActive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicle return can only be recorded for a confirmed or active booking");
        }
        if (!paymentRepository.existsByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESSFUL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment must be completed before recording a return");
        }
        if (handoverRepository.existsByBookingIdAndType(bookingId, HandoverType.DROPOFF)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Drop-off has already been recorded for this booking");
        }
        if (damageNoted && (damageDescription == null || damageDescription.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Damage description is required when damage is noted");
        }

        if (isConfirmed && !handoverRepository.existsByBookingIdAndType(bookingId, HandoverType.PICKUP)) {
            // Pickup was never recorded — create a backdated record so the audit trail is complete.
            String note = "Pickup recorded retroactively at return time. Original pickup was not captured in the system.";
            VehicleHandover retroPickup = buildHandoverAt(booking, HandoverType.PICKUP,
                    null, null, note, false, null, null,
                    authentication.getName(), ipAddress, userAgent,
                    booking.getStartTime());
            handoverRepository.save(retroPickup);
            booking.setStatus(BookingStatus.ACTIVE);
            bookingRepository.save(booking);
            disbursementService.createForPickup(bookingId);
        }

        VehicleHandover dropoff = buildHandover(booking, HandoverType.DROPOFF, odometerReading,
                fuelLevel, conditionNotes, damageNoted, damageDescription, location,
                authentication.getName(), ipAddress, userAgent);
        VehicleHandover savedDropoff = handoverRepository.save(dropoff);
        saveSignature(dropoffSignature, savedDropoff, HandoverType.DROPOFF);
        saveOdometerPhoto(dropoffOdometerPhoto, savedDropoff);

        booking.setActualEndTime(ZonedDateTime.now());
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        overstayChargeService.createIfOverstay(booking);

        return handoverRepository.findByBookingIdOrderByHandoverTimeAsc(bookingId)
                .stream().map(HandoverResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Resource getOdometerPhoto(UUID handoverId, Authentication authentication) {
        VehicleHandover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Handover not found"));
        boolean renter = handover.getBooking().getUser().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        boolean owner = handover.getBooking().getVehicle().getOwner().getEmailAddress()
                .equalsIgnoreCase(authentication.getName());
        if (!renter && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this handover record");
        }
        if (handover.getOdometerPhotoFileName() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No odometer photo for this handover");
        }
        try {
            Resource resource = new UrlResource(handoverDirectory.resolve(handover.getOdometerPhotoFileName()).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Odometer photo file not found");
            }
            return resource;
        } catch (java.net.MalformedURLException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Odometer photo file not found", ex);
        }
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
        return buildHandoverAt(booking, type, odometerReading, fuelLevel, conditionNotes,
                damageNoted, damageDescription, location, signerEmail, ipAddress, userAgent,
                ZonedDateTime.now());
    }

    private VehicleHandover buildHandoverAt(Booking booking, HandoverType type,
                                            Integer odometerReading, FuelLevel fuelLevel,
                                            String conditionNotes, boolean damageNoted,
                                            String damageDescription, String location,
                                            String signerEmail, String ipAddress, String userAgent,
                                            ZonedDateTime handoverTime) {
        VehicleHandover h = new VehicleHandover();
        h.setBooking(booking);
        h.setType(type);
        h.setHandoverTime(handoverTime);
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

    private void saveOdometerPhoto(MultipartFile photo, VehicleHandover handover) {
        if (photo == null || photo.isEmpty()) return;
        if (photo.getContentType() == null || !photo.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Odometer photo must be an image file");
        }
        String ext = resolveExtension(photo.getContentType());
        String fileName = handover.getId() + "-odometer." + ext;
        try {
            Files.createDirectories(handoverDirectory);
            Files.copy(photo.getInputStream(), handoverDirectory.resolve(fileName));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store odometer photo", ex);
        }
        handover.setOdometerPhotoFileName(fileName);
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

    private void requireRenterOrOwnerOrManager(Booking booking, Authentication authentication) {
        boolean renter  = booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean owner   = booking.getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean manager = has(authentication, "BOOKING_MANAGE")
                || has(authentication, "ROLE_ADMIN")
                || has(authentication, "ROLE_SUPER_ADMIN");
        if (!renter && !owner && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the renter, vehicle owner, or booking staff can record a vehicle return");
        }
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
