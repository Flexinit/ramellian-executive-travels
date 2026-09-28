package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.RentalTermRequest;
import com.ramellian_executive.travels.model.RentalTermResponse;
import com.ramellian_executive.travels.service.RentalTermService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rental-terms")
public class RentalTermController {

    private final RentalTermService rentalTermService;

    @GetMapping
    public List<RentalTermResponse> listActive() {
        return rentalTermService.listActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('RENTAL_TERM_MANAGE') or hasRole('ADMIN')")
    public List<RentalTermResponse> listAll() {
        return rentalTermService.listAll();
    }

    @GetMapping("/category/{category}")
    public List<RentalTermResponse> listByCategory(@PathVariable String category) {
        return rentalTermService.listByCategory(category);
    }

    @GetMapping("/{id}")
    public RentalTermResponse getById(@PathVariable UUID id) {
        return rentalTermService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RENTAL_TERM_MANAGE') or hasRole('ADMIN')")
    public RentalTermResponse create(@Valid @RequestBody RentalTermRequest request,
                                     Authentication authentication) {
        return rentalTermService.create(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RENTAL_TERM_MANAGE') or hasRole('ADMIN')")
    public RentalTermResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody RentalTermRequest request,
                                     Authentication authentication) {
        return rentalTermService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('RENTAL_TERM_MANAGE') or hasRole('ADMIN')")
    public RentalTermResponse toggleEnabled(@PathVariable UUID id, Authentication authentication) {
        return rentalTermService.toggleEnabled(id, authentication);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RENTAL_TERM_MANAGE') or hasRole('SUPER_ADMIN')")
    public void delete(@PathVariable UUID id, Authentication authentication) {
        rentalTermService.delete(id, authentication);
    }
}
