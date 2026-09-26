package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.PermissionRequest;
import com.ramellian_executive.travels.model.PermissionResponse;
import com.ramellian_executive.travels.service.RolePermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final RolePermissionService rolePermissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ') or hasAuthority('PERMISSION_MANAGE') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public List<PermissionResponse> listPermissions() {
        return rolePermissionService.listPermissions();
    }

    @GetMapping("/{idOrName}")
    @PreAuthorize("hasAuthority('PERMISSION_READ') or hasAuthority('PERMISSION_MANAGE') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public PermissionResponse getPermission(@PathVariable String idOrName) {
        return rolePermissionService.getPermission(idOrName);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public PermissionResponse createPermission(@Valid @RequestBody PermissionRequest request, Authentication authentication) {
        return rolePermissionService.createPermission(request, authentication);
    }

    @PutMapping("/{idOrName}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public PermissionResponse updatePermission(@PathVariable String idOrName,
                                               @RequestBody PermissionRequest request,
                                               Authentication authentication) {
        return rolePermissionService.updatePermission(idOrName, request, authentication);
    }

    @DeleteMapping("/{idOrName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE') or hasRole('SUPER_ADMIN')")
    public void deletePermission(@PathVariable String idOrName, Authentication authentication) {
        rolePermissionService.deletePermission(idOrName, authentication);
    }
}
