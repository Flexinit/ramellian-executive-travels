package com.ramellian_executive.travels.service.vehicle;

import com.ramellian_executive.travels.entity.BookingStatus;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.entity.VehicleApprovalStatus;
import com.ramellian_executive.travels.model.VehicleRequest;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.model.VehicleReviewRequest;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehiclePhotoRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import com.ramellian_executive.travels.service.AuditLogService;
import com.ramellian_executive.travels.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehiclePhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final VerificationNotificationService emailNotificationService;
    private final AuditLogService auditLogService;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this(vehicleRepository, null, userRepository, null, null, null);
    }

    @Autowired
    public VehicleService(VehicleRepository vehicleRepository,
                          VehiclePhotoRepository photoRepository,
                          UserRepository userRepository,
                          NotificationService notificationService,
                          VerificationNotificationService emailNotificationService,
                          AuditLogService auditLogService) {
        this.vehicleRepository = vehicleRepository;
        this.photoRepository = photoRepository;
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
        List<Vehicle> vehicles = vehicleRepository.findByAvailableTrueAndApprovalStatus(VehicleApprovalStatus.APPROVED);
        Map<UUID, List<String>> photos = photosByVehicleId(vehicles.stream().map(Vehicle::getId).toList());
        return vehicles.stream().map(v -> VehicleResponse.from(v, false, photos.getOrDefault(v.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listPendingVehicles() {
        List<Vehicle> vehicles = vehicleRepository.findByApprovalStatusOrderByCreatedAtDesc(VehicleApprovalStatus.PENDING);
        Map<UUID, List<String>> photos = photosByVehicleId(vehicles.stream().map(Vehicle::getId).toList());
        return vehicles.stream().map(v -> VehicleResponse.from(v, false, photos.getOrDefault(v.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listMyVehicles(Authentication authentication) {
        List<Vehicle> vehicles = vehicleRepository.findByOwnerEmailAddressIgnoreCaseOrderByCreatedAtDesc(authentication.getName());
        Map<UUID, List<String>> photos = photosByVehicleId(vehicles.stream().map(Vehicle::getId).toList());
        return vehicles.stream().map(v -> VehicleResponse.from(v, false, photos.getOrDefault(v.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listAllVehiclesForAdmin(VehicleApprovalStatus status) {
        List<Vehicle> vehicles = status != null
                ? vehicleRepository.findByApprovalStatusOrderByCreatedAtDesc(status)
                : vehicleRepository.findAllByOrderByCreatedAtDesc();
        Map<UUID, List<String>> photos = photosByVehicleId(vehicles.stream().map(Vehicle::getId).toList());
        return vehicles.stream().map(v -> VehicleResponse.from(v, false, photos.getOrDefault(v.getId(), List.of()))).toList();
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
    public Page<VehicleResponse> search(String query, String location, Double minPrice, Double maxPrice,
                                        Integer capacity, String fuelType, String transmissionType,
                                        Boolean selfDrive, Boolean chauffeured, UUID ownerId,
                                        java.util.Set<com.ramellian_executive.travels.entity.TripType> tripTypes,
                                        ZonedDateTime startTime, ZonedDateTime endTime,
                                        String sort, int page, int size) {
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

        // All sort properties must be Vehicle-own fields. PostgreSQL rejects ORDER BY on
        // columns not in the SELECT list when the query uses SELECT DISTINCT.
        Sort dbSort = switch (sort == null ? "recommended" : sort.trim().toLowerCase()) {
            case "price_asc"    -> Sort.by(Sort.Direction.ASC, "pricePerDay");
            case "price_desc"   -> Sort.by(Sort.Direction.DESC, "pricePerDay");
            case "rating_desc"  -> Sort.by(Sort.Direction.DESC, "rating");
            case "capacity_desc"-> Sort.by(Sort.Direction.DESC, "capacity");
            case "recommended"  -> Sort.by(Sort.Order.desc("rating"), Sort.Order.asc("pricePerDay"));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sort must be recommended, price_asc, price_desc, rating_desc, or capacity_desc");
        };

        java.util.Set<com.ramellian_executive.travels.entity.TripType> resolvedTripTypes =
                (tripTypes == null || tripTypes.isEmpty()) ? null : tripTypes;
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime effectiveStart = startTime != null ? startTime : now;
        java.time.ZonedDateTime effectiveEnd = endTime != null ? endTime : now.plusDays(1);
        java.util.Set<java.util.UUID> bookedIds = new java.util.HashSet<>(
                vehicleRepository.findBookedVehicleIds(
                        List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE),
                        effectiveStart, effectiveEnd));

        Page<Vehicle> vehiclePage = vehicleRepository.search(blankToNull(query),
                blankToNull(location), minPrice, maxPrice, capacity, blankToNull(fuelType),
                blankToNull(transmissionType), selfDrive, chauffeured, ownerId, resolvedTripTypes, startTime, endTime,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE),
                PageRequest.of(page, size, dbSort));
        Map<UUID, List<String>> photos = photosByVehicleId(
                vehiclePage.getContent().stream().map(Vehicle::getId).collect(Collectors.toSet()));
        return vehiclePage.map(v -> VehicleResponse.from(v, bookedIds.contains(v.getId()),
                photos.getOrDefault(v.getId(), List.of())));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Batch-fetches photos for a collection of vehicles and groups them by vehicle ID. */
    private Map<UUID, List<String>> photosByVehicleId(java.util.Collection<UUID> ids) {
        if (photoRepository == null || ids.isEmpty()) return Map.of();
        return photoRepository.findByVehicleIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        p -> p.getVehicle().getId(),
                        Collectors.mapping(p -> p.getUrl(), Collectors.toList())));
    }

    private List<String> photosFor(UUID vehicleId) {
        if (photoRepository == null) return List.of();
        return photoRepository.findByVehicleId(vehicleId).stream()
                .map(p -> p.getUrl())
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(UUID id) {
        Vehicle vehicle = findVehicle(id);
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
        boolean booked = vehicleRepository.findBookedVehicleIds(
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE),
                now, now.plusDays(1)).contains(vehicle.getId());
        return VehicleResponse.from(vehicle, booked, photosFor(vehicle.getId()));
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
        vehicle.setYear(request.year());
        vehicle.setInsurer(request.insurer() != null ? request.insurer().trim() : null);
        vehicle.setPolicyNumber(request.policyNumber() != null ? request.policyNumber().trim() : null);
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
