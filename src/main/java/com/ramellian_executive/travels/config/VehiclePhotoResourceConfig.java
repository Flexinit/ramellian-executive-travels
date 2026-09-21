package com.ramellian_executive.travels.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class VehiclePhotoResourceConfig implements WebMvcConfigurer {
    private final String location;

    public VehiclePhotoResourceConfig(
            @Value("${app.vehicle.photo-directory:uploads/vehicles}") String directory) {
        location = Paths.get(directory).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/vehicles/**")
                .addResourceLocations(location);
    }
}
