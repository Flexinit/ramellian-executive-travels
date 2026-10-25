package com.ramellian_executive.travels.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
public class VehiclePhotoResourceConfig implements WebMvcConfigurer {
    private final String location;

    public VehiclePhotoResourceConfig(
            @Value("${app.vehicle.photo-directory:uploads/vehicles}") String directory) {
        String uri = Paths.get(directory).toAbsolutePath().normalize().toUri().toString();
        // Spring's resource handler requires the location to end with '/' to resolve
        // sub-paths correctly. Path.toUri() omits the trailing slash when the directory
        // does not exist yet (e.g., fresh deploy before first upload).
        location = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/vehicles/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable());
    }
}
