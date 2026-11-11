package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.entity.FuelLevel;
import com.ramellian_executive.travels.model.HandoverResponse;
import com.ramellian_executive.travels.service.VehicleHandoverService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/handovers")
public class VehicleHandoverController {

    private final VehicleHandoverService handoverService;

    @PostMapping(value = "/bookings/{bookingId}/pickup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HandoverResponse pickup(
            @PathVariable UUID bookingId,
            @RequestPart MultipartFile signature,
            @RequestPart(required = false) MultipartFile odometerPhoto,
            @RequestParam Integer odometerReading,
            @RequestParam FuelLevel fuelLevel,
            @RequestParam(required = false) String conditionNotes,
            @RequestParam(defaultValue = "false") boolean damageNoted,
            @RequestParam(required = false) String damageDescription,
            @RequestParam(required = false) String location,
            Authentication authentication,
            HttpServletRequest request) {
        return handoverService.pickup(bookingId, signature, odometerPhoto, odometerReading, fuelLevel,
                conditionNotes, damageNoted, damageDescription, location,
                authentication, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping(value = "/bookings/{bookingId}/dropoff", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HandoverResponse dropoff(
            @PathVariable UUID bookingId,
            @RequestPart MultipartFile signature,
            @RequestPart(required = false) MultipartFile odometerPhoto,
            @RequestParam Integer odometerReading,
            @RequestParam FuelLevel fuelLevel,
            @RequestParam(required = false) String conditionNotes,
            @RequestParam(defaultValue = "false") boolean damageNoted,
            @RequestParam(required = false) String damageDescription,
            @RequestParam(required = false) String location,
            Authentication authentication,
            HttpServletRequest request) {
        return handoverService.dropoff(bookingId, signature, odometerPhoto, odometerReading, fuelLevel,
                conditionNotes, damageNoted, damageDescription, location,
                authentication, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping(value = "/bookings/{bookingId}/return", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<HandoverResponse> recordReturn(
            @PathVariable UUID bookingId,
            @RequestPart(required = false) MultipartFile signature,
            @RequestPart(required = false) MultipartFile odometerPhoto,
            @RequestParam(required = false) Integer odometerReading,
            @RequestParam(required = false) FuelLevel fuelLevel,
            @RequestParam(required = false) String conditionNotes,
            @RequestParam(defaultValue = "false") boolean damageNoted,
            @RequestParam(required = false) String damageDescription,
            @RequestParam(required = false) String location,
            Authentication authentication,
            HttpServletRequest request) {
        return handoverService.recordReturn(bookingId, signature, odometerPhoto, odometerReading,
                fuelLevel, conditionNotes, damageNoted, damageDescription, location,
                authentication, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @GetMapping("/bookings/{bookingId}")
    public List<HandoverResponse> getByBooking(@PathVariable UUID bookingId,
                                               Authentication authentication) {
        return handoverService.getByBooking(bookingId, authentication);
    }

    @GetMapping("/{handoverId}/odometer-photo")
    public ResponseEntity<Resource> odometerPhoto(@PathVariable UUID handoverId,
                                                   Authentication authentication) {
        Resource resource = handoverService.getOdometerPhoto(handoverId, authentication);
        String contentType = MediaType.IMAGE_JPEG_VALUE;
        try {
            String filename = resource.getFilename();
            if (filename != null) {
                if (filename.endsWith(".png")) contentType = MediaType.IMAGE_PNG_VALUE;
                else if (filename.endsWith(".gif")) contentType = "image/gif";
                else if (filename.endsWith(".webp")) contentType = "image/webp";
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);
    }
}
