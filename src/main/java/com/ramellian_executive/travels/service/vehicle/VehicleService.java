package com.ramellian_executive.travels.service.vehicle;

import com.ramellian_executive.travels.entity.BookingStatus;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.entity.VehicleApprovalStatus;
import com.ramellian_executive.travels.model.VehicleRequest;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.model.VehicleReviewRequest;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import com.ramellian_executive.travels.service.AuditLogService;
import com.ramellian_executive.travels.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final VerificationNotificationService emailNotificationService;
    private final AuditLogService auditLogService;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this(vehicleRepository, userRepository, null, null, null);
    }

    @Autowired
    public VehicleService(VehicleRepository vehicleRepository,
                          UserRepository userRepository,
                          NotificationService notificationService,
                          VerificationNotificationService emailNotificationService,
                          AuditLogService auditLogService) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailNotificationService = emailNotificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public VehicleResponse createVehicle(VehicleRequest request, Authentication authentication) {
        User owner = findUser(authentication.getName());
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        apply(vehicle, request);
        // Newly created vehicle is pending admin approval and cannot be available yet
        vehicle.setApprovalStatus(VehicleApprovalStatus.PENDING);
        vehicle.setAvailable(false);
        owner.setAddedVehicleToProfile(true);
        userRepository.save(owner);
        Vehicle saved = vehicleRepository.save(vehicle);

        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    "VEHICLE_CREATE",
                    "VEHICLE",
                    saved.getId() != null ? saved.getId().toString() : null,
                    "Created vehicle " + saved.getModel() + " (" + saved.getRegistrationNumber() + ") pending approval",
                    "SUCCESS"
            );
        }

        return VehicleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listAvailableVehicles() {
        return vehicleRepository.findByAvailableTrueAndApprovalStatus(VehicleApprovalStatus.APPROVED)
                .stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listPendingVehicles() {
        return vehicleRepository.findByApprovalStatusOrderByCreatedAtDesc(VehicleApprovalStatus.PENDING)
                .stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listMyVehicles(Authentication authentication) {
        return vehicleRepository.findByOwnerEmailAddressIgnoreCaseOrderByCreatedAtDesc(authentication.getName())
                .stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listAllVehiclesForAdmin(VehicleApprovalStatus status) {
        if (status != null) {
            return vehicleRepository.findByApprovalStatusOrderByCreatedAtDesc(status)
                    .stream().map(VehicleResponse::from).toList();
        }
        return vehicleRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(VehicleResponse::from).toList();
    }

    @Transactional
    public VehicleResponse reviewVehicle(UUID id, VehicleReviewRequest request, Authentication authentication) {
        Vehicle vehicle = findVehicle(id);
        String status = request.status() != null ? request.status().trim().toUpperCase(Locale.ROOT) : "";
        VehicleApprovalStatus reviewStatus;
        try {
            reviewStatus = VehicleApprovalStatus.valueOf(status);
            if (reviewStatus == VehicleApprovalStatus.PENDING) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be APPROVED or REJECTED");
        }

        vehicle.setApprovalStatus(reviewStatus);
        vehicle.setApprovalNotes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null);
        vehicle.setReviewedBy(authentication != null ? authentication.getName() : "ADMIN");
        vehicle.setReviewedAt(ZonedDateTime.now());

        if (reviewStatus == VehicleApprovalStatus.APPROVED) {
            vehicle.setAvailable(true);
        } else {
            vehicle.setAvailable(false);
        }

        Vehicle saved = vehicleRepository.save(vehicle);

        if (notificationService != null && saved.getOwner() != null) {
            String displayStatus = reviewStatus == VehicleApprovalStatus.APPROVED ? "Approved" : "Rejected";
            String vehicleTitle = (saved.getModel() != null ? saved.getModel() : "Vehicle")
                    + (saved.getRegistrationNumber() != null && !saved.getRegistrationNumber().isBlank()
                    ? " (" + saved.getRegistrationNumber() + ")" : "");
            String noteSuffix = (saved.getApprovalNotes() != null && !saved.getApprovalNotes().isBlank())
                    ? " Notes: " + saved.getApprovalNotes()
                    : "";
            notificationService.createNotification(
                    saved.getOwner(),
                    "Vehicle " + displayStatus,
                    "Your vehicle " + vehicleTitle + " has been " + displayStatus.toLowerCase() + " by an administrator." + noteSuffix,
                    "VEHICLE_" + reviewStatus.name(),
                    saved.getId(),
                    "VEHICLE"
            );
        }

        if (emailNotificationService != null) {
            emailNotificationService.sendVehicleReviewNotification(saved);
        }

        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    reviewStatus == VehicleApprovalStatus.APPROVED ? "VEHICLE_APPROVAL" : "VEHICLE_REJECTION",
                    "VEHICLE",
                    saved.getId() != null ? saved.getId().toString() : null,
                    "Reviewed vehicle " + saved.getModel() + " (" + saved.getRegistrationNumber() + ") status: " + reviewStatus,
                    "SUCCESS"
            );
        }

        return VehicleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> search(String query, String location, Double minPrice, Double maxPrice,
                                        Integer capacity, String fuelType, String transmissionType,
                                        Boolean selfDrive, Boolean chauffeured, UUID ownerId,
                                        java.util.Set<com.ramellian_executive.travels.entity.TripType> tripTypes,
                                        ZonedDateTime startTime, ZonedDateTime endTime,
                                        String sort) {
        if ((startTime == null) != (endTime == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startTime and endTime must be provided together");
        }
        if (startTime != null && !endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endTime must be after startTime");
        }
        if (minPrice != null && maxPrice != null && maxPrice < minPrice) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "maxPrice must be greater than or equal to minPrice");
        }
        java.util.Set<com.ramellian_executive.travels.entity.TripType> resolvedTripTypes =
                (tripTypes == null || tripTypes.isEmpty()) ? null : tripTypes;
        List<VehicleResponse> vehicles = vehicleRepository.search(blankToNull(query),
                blankToNull(location), minPrice, maxPrice, capacity, blankToNull(fuelType),
                blankToNull(transmissionType), selfDrive, chauffeured, ownerId, resolvedTripTypes, startTime, endTime,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE))
                .stream().map(VehicleResponse::from).toList();
        return switch (sort == null ? "recommended" : sort.trim().toLowerCase()) {
            case "price_asc" -> vehicles.stream().sorted(java.util.Comparator.comparing(
                    VehicleResponse::pricePerDay)).toList();
            case "price_desc" -> vehicles.stream().sorted(java.util.Comparator.comparing(
                    VehicleResponse::pricePerDay).reversed()).toList();
            case "rating_desc" -> vehicles.stream().sorted(java.util.Comparator.comparing(
                    v -> v.rating() == null ? 0.0 : v.rating(), java.util.Comparator.reverseOrder())).toList();
            case "capacity_desc" -> vehicles.stream().sorted(java.util.Comparator.comparingInt(
                    VehicleResponse::capacity).reversed()).toList();
            case "recommended" -> vehicles.stream().sorted(java.util.Comparator
                    .comparing((VehicleResponse v) -> v.rating() == null ? 0.0 : v.rating()).reversed()
                    .thenComparing(VehicleResponse::pricePerDay)).toList();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sort must be recommended, price_asc, price_desc, rating_desc, or capacity_desc");
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(UUID id) {
        return VehicleResponse.from(findVehicle(id));
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID id, VehicleRequest request, Authentication authentication) {
        Vehicle vehicle = findVehicle(id);
        ensureOwnerOrManager(vehicle, authentication);

        // Admin approval is mandatory before a vehicle can be set as available
        if (request.available() && vehicle.getApprovalStatus() != VehicleApprovalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vehicle must be approved by an administrator before it can be made available");
        }

        apply(vehicle, request);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void deleteVehicle(UUID id, Authentication authentication) {
        Vehicle vehicle = findVehicle(id);
        ensureOwnerOrManager(vehicle, authentication);
        vehicleRepository.delete(vehicle);
    }

    private void apply(Vehicle vehicle, VehicleRequest request) {
        vehicle.setModel(request.model().trim());
        vehicle.setVIN(request.vin().trim());
        vehicle.setRegistrationNumber(request.registrationNumber().trim());
        vehicle.setLocation(request.location().trim());
        vehicle.setPricePerDay(request.pricePerDay());
        vehicle.setCapacity(request.capacity());
        vehicle.setColor(request.color().trim());
        vehicle.setFuelType(request.fuelType().trim());
        vehicle.setTransmissionType(request.transmissionType().trim());
        vehicle.setSelfDrive(request.selfDrive());
        vehicle.setChauffeured(request.chauffeured());
        if (vehicle.getApprovalStatus() == VehicleApprovalStatus.APPROVED) {
            vehicle.setAvailable(request.available());
        } else {
            vehicle.setAvailable(false);
        }
        vehicle.setAvailableFrom(request.availableFrom());
        vehicle.setAvailableTo(request.availableTo());
        vehicle.setHasWorkingAC(request.hasWorkingAC());
        vehicle.setComprehensiveInsurance(request.comprehensiveInsurance());
        vehicle.setInsuranceVerificationStickerPhoto(
                request.insuranceVerificationStickerPhoto() != null
                        ? request.insuranceVerificationStickerPhoto().trim()
                        : null);
        if (request.tripTypes() != null) {
            vehicle.setTripTypes(new java.util.HashSet<>(request.tripTypes()));
        } else {
            vehicle.setTripTypes(new java.util.HashSet<>());
        }
    }

    private void ensureOwnerOrManager(Vehicle vehicle, Authentication authentication) {
        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), "VEHICLE_MANAGE"));
        if (!manager && !vehicle.getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify this vehicle");
        }
    }

    private Vehicle findVehicle(UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmailAddressIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
    }
}
