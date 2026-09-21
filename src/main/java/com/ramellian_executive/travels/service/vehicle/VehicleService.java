package com.ramellian_executive.travels.service.vehicle;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.model.VehicleRequest;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.ZonedDateTime;
import com.ramellian_executive.travels.entity.BookingStatus;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    @Transactional
    public VehicleResponse createVehicle(VehicleRequest request, Authentication authentication) {
        User owner = findUser(authentication.getName());
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        apply(vehicle, request);
        vehicle.setAvailable(false);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listAvailableVehicles() {
        return vehicleRepository.findByAvailableTrue().stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> search(String query, String location, Double minPrice, Double maxPrice,
                                        Integer capacity, String fuelType, String transmissionType,
                                        Boolean selfDrive, Boolean chauffeured,
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
                blankToNull(transmissionType), selfDrive, chauffeured, resolvedTripTypes, startTime, endTime,
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
        if (request.tripTypes() != null) {
            vehicle.setTripTypes(new java.util.HashSet<>(request.tripTypes()));
        }
    }

    private void ensureOwnerOrManager(Vehicle vehicle, Authentication authentication) {
        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("VEHICLE_MANAGE"));
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
