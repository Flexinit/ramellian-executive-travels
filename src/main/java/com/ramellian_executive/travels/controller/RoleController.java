package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.RoleCreateRequest;
import com.ramellian_executive.travels.model.RoleResponse;
import com.ramellian_executive.travels.model.RoleUpdateRequest;
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
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RolePermissionService rolePermissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public List<RoleResponse> listRoles() {
        return rolePermissionService.listRoles();
    }

    @GetMapping("/{idOrName}")
    @PreAuthorize("hasAuthority('ROLE_READ') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public RoleResponse getRole(@PathVariable String idOrName) {
        return rolePermissionService.getRole(idOrName);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_CREATE') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public RoleResponse createRole(@Valid @RequestBody RoleCreateRequest request, Authentication authentication) {
        return rolePermissionService.createRole(request, authentication);
    }

    @PutMapping("/{idOrName}")
    @PreAuthorize("hasAuthority('ROLE_UPDATE') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public RoleResponse updateRole(@PathVariable String idOrName,
                                   @RequestBody RoleUpdateRequest request,
                                   Authentication authentication) {
        return rolePermissionService.updateRole(idOrName, request, authentication);
    }

    @DeleteMapping("/{idOrName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_DELETE') or hasAuthority('ROLE_MANAGE') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public void deleteRole(@PathVariable String idOrName, Authentication authentication) {
        rolePermissionService.deleteRole(idOrName, authentication);
    }
}
