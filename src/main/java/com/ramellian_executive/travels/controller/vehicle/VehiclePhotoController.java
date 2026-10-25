package com.ramellian_executive.travels.controller.vehicle;
import com.ramellian_executive.travels.model.VehiclePhotoResponse;
import com.ramellian_executive.travels.repository.vehicle.VehiclePhotoRepository;
import com.ramellian_executive.travels.service.vehicle.VehiclePhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vehicles")
public class VehiclePhotoController {

    private final VehiclePhotoService service;
    private final VehiclePhotoRepository photoRepository;

    @GetMapping("/{id}/photos")
    public List<VehiclePhotoResponse> getPhotos(@PathVariable UUID id) {
        return photoRepository.findByVehicleId(id).stream()
                .map(p -> new VehiclePhotoResponse(p.getId(), p.getFileName(), p.getContentType(), p.getUrl()))
                .toList();
    }

    @PostMapping("/{id}/photos")
    public VehiclePhotoResponse upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file, Authentication authentication) {
        return service.upload(id, file, authentication);
    }

    @PreAuthorize("hasAuthority('VEHICLE_DELETE')")
    @DeleteMapping("/{id}/photos/{photoId}")
    public void deleteCarPhoto(@PathVariable UUID id, @PathVariable UUID photoId, Authentication authentication) {
        service.deleteCarPhoto(id, photoId, authentication);
    }
}
