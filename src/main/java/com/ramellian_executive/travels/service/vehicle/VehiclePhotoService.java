package com.ramellian_executive.travels.service.vehicle;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.VehiclePhotoResponse;
import com.ramellian_executive.travels.repository.vehicle.VehiclePhotoRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class VehiclePhotoService {
    private final VehicleRepository vehicleRepository;
    private final VehiclePhotoRepository photoRepository;
    private final Path directory;

    @Autowired
    public VehiclePhotoService(VehicleRepository vehicleRepository, VehiclePhotoRepository photoRepository,
                               @org.springframework.beans.factory.annotation.Value("${app.vehicle.photo-directory:uploads/vehicles}") String directory) {
        this.vehicleRepository = vehicleRepository;
        this.photoRepository = photoRepository;
        this.directory = Paths.get(directory).toAbsolutePath().normalize();
    }
    public VehiclePhotoService(VehicleRepository vehicleRepository, String directory) {
        this.vehicleRepository = vehicleRepository;
        this.photoRepository = null;
        this.directory = Paths.get(directory).toAbsolutePath().normalize();
    }

    public VehiclePhotoResponse upload(UUID id, MultipartFile file, Authentication authentication) {
        Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!vehicle.getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot upload to this vehicle");
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An image file is required");
        String extension = OptionalExtension.from(file.getOriginalFilename());
        String name = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(directory);
            Files.copy(file.getInputStream(), directory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Photo could not be stored", ex); }
        VehiclePhoto photo = new VehiclePhoto(); photo.setVehicle(vehicle); photo.setFileName(name); photo.setContentType(file.getContentType()); photo.setUrl("/uploads/vehicles/" + name);
        if (photoRepository != null) photo = photoRepository.save(photo);
        return new VehiclePhotoResponse(photo.getId(), name, photo.getContentType(), photo.getUrl());
    }
    private static class OptionalExtension {
        static String from(String name) { if (name == null || !name.contains(".")) return ""; return name.substring(name.lastIndexOf('.')).replaceAll("[^a-zA-Z0-9.]", ""); }
    }
}
