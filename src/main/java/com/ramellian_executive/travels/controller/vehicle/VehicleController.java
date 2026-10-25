package com.ramellian_executive.travels.controller.vehicle;

import com.ramellian_executive.travels.entity.TripType;
import com.ramellian_executive.travels.entity.VehicleApprovalStatus;
import com.ramellian_executive.travels.model.VehicleRequest;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.model.VehicleReviewRequest;
import com.ramellian_executive.travels.service.vehicle.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles", description = "Endpoints for vehicle management, searches, and admin approval")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "Search and list available approved vehicles")
    public Page<VehicleResponse> listAvailableVehicles(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) String transmissionType,
            @RequestParam(required = false) Boolean selfDrive,
            @RequestParam(required = false) Boolean chauffeured,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) Set<TripType> tripTypes,
            @RequestParam(required = false) ZonedDateTime startTime,
            @RequestParam(required = false) ZonedDateTime endTime,
            @RequestParam(defaultValue = "recommended") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return vehicleService.search(query, location, minPrice, maxPrice, capacity, fuelType,
                transmissionType, selfDrive, chauffeured, ownerId, tripTypes, startTime, endTime, sort, page, size);
    }

    @GetMapping("/me")
    @Operation(summary = "List vehicles owned by current user")
    public List<VehicleResponse> getMyVehicles(Authentication authentication) {
        return vehicleService.listMyVehicles(authentication);
    }

    @GetMapping("/admin/pending")
    @Operation(summary = "List all vehicles pending administrative approval")
    @PreAuthorize("hasAuthority('VEHICLE_REVIEW') or hasAuthority('VEHICLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public List<VehicleResponse> getPendingVehicles() {
        return vehicleService.listPendingVehicles();
    }

    @GetMapping({"/admin/all", "/admin"})
    @Operation(summary = "List all vehicles for admin with optional approval status filter")
    @PreAuthorize("hasAuthority('VEHICLE_MANAGE') or hasAuthority('VEHICLE_REVIEW') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public List<VehicleResponse> getAllVehiclesForAdmin(@RequestParam(required = false) VehicleApprovalStatus status) {
        return vehicleService.listAllVehiclesForAdmin(status);
    }

    @PutMapping({"/admin/{id}/review", "/{id}/review"})
    @Operation(summary = "Review and approve or reject a vehicle")
    @PreAuthorize("hasAuthority('VEHICLE_REVIEW') or hasAuthority('VEHICLE_APPROVE') or hasAuthority('VEHICLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public VehicleResponse reviewVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleReviewRequest request,
            Authentication authentication) {
        return vehicleService.reviewVehicle(id, request, authentication);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vehicle details by ID")
    public VehicleResponse getVehicle(@PathVariable UUID id) {
        return vehicleService.getVehicle(id);
    }

    @PostMapping({"/create-vehicle", ""})
    @Operation(summary = "Create a new vehicle listing (pending approval)")
    public VehicleResponse createVehicle(@Valid @RequestBody VehicleRequest request, Authentication authentication) {
        return vehicleService.createVehicle(request, authentication);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update vehicle listing")
    public VehicleResponse updateVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleRequest request,
            Authentication authentication) {
        return vehicleService.updateVehicle(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete vehicle listing")
    public void deleteVehicle(@PathVariable UUID id, Authentication authentication) {
        vehicleService.deleteVehicle(id, authentication);
    }
}
