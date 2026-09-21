package com.ramellian_executive.travels.controller.vehicle;

import com.ramellian_executive.travels.entity.TripType;
import com.ramellian_executive.travels.model.VehicleRequest;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.service.vehicle.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.ZonedDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public List<VehicleResponse> listAvailableVehicles(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Integer capacity,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) String transmissionType,
            @RequestParam(required = false) Boolean selfDrive,
            @RequestParam(required = false) Boolean chauffeured,
            @RequestParam(required = false) Set<TripType> tripTypes,
            @RequestParam(required = false) ZonedDateTime startTime,
            @RequestParam(required = false) ZonedDateTime endTime,
            @RequestParam(defaultValue = "recommended") String sort) {
        return vehicleService.search(query, location, minPrice, maxPrice, capacity, fuelType,
                transmissionType, selfDrive, chauffeured, tripTypes, startTime, endTime, sort);
    }



    @GetMapping("/{id}")
    public VehicleResponse getVehicle(@PathVariable UUID id) {
        return vehicleService.getVehicle(id);
    }

    @PostMapping("/create-vehicle")
    public VehicleResponse createVehicle(@Valid @RequestBody VehicleRequest request, Authentication authentication) {
        return vehicleService.createVehicle(request, authentication);
    }

    @PutMapping("/{id}")
    public VehicleResponse updateVehicle(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleRequest request,
            Authentication authentication) {
        return vehicleService.updateVehicle(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    public void deleteVehicle(@PathVariable UUID id, Authentication authentication) {
        vehicleService.deleteVehicle(id, authentication);
    }
}
