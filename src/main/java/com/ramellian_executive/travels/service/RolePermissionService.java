package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.Permission;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.PermissionRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    private static final Set<String> BUILT_IN_ROLES = Set.of(
            UserRole.SUPER_ADMIN,
            UserRole.ADMIN,
            UserRole.CAR_OWNER,
            UserRole.CAR_RENTER,
            UserRole.CHAUFFEUR
    );

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return userRoleRepository.findAll().stream()
                .sorted(Comparator.comparing(UserRole::getRoleName))
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(String idOrName) {
        return RoleResponse.from(findRole(idOrName));
    }

    @Transactional
    public RoleResponse createRole(RoleCreateRequest request, Authentication authentication) {
        String roleName = request.roleName() != null ? request.roleName().trim().toUpperCase(Locale.ROOT) : "";
        if (roleName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name cannot be blank");
        }
        if (userRoleRepository.existsByRoleNameIgnoreCase(roleName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role with name '" + roleName + "' already exists");
        }

        UserRole role = new UserRole();
        role.setRoleName(roleName);
        role.setDescription(request.description() != null ? request.description().trim() : null);

        if (request.permissions() != null && !request.permissions().isEmpty()) {
            Set<Permission> perms = resolvePermissions(request.permissions());
            role.setPermissions(perms);
        }

        UserRole saved = userRoleRepository.save(role);
        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    "ROLE_CREATE",
                    "ROLE",
                    saved.getId() != null ? saved.getId().toString() : null,
                    "Created role: " + saved.getRoleName(),
                    "SUCCESS"
            );
        }
        return RoleResponse.from(saved);
    }

    @Transactional
    public RoleResponse updateRole(String idOrName, RoleUpdateRequest request, Authentication authentication) {
        UserRole role = findRole(idOrName);

        if (request.description() != null) {
            role.setDescription(request.description().trim());
        }

        if (request.permissions() != null) {
            Set<Permission> perms = resolvePermissions(request.permissions());
            role.setPermissions(perms);
        }

        UserRole saved = userRoleRepository.save(role);
        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    "ROLE_UPDATE",
                    "ROLE",
                    saved.getId() != null ? saved.getId().toString() : null,
                    "Updated role: " + saved.getRoleName(),
                    "SUCCESS"
            );
        }
        return RoleResponse.from(saved);
    }

    @Transactional
    public void deleteRole(String idOrName, Authentication authentication) {
        UserRole role = findRole(idOrName);
        if (BUILT_IN_ROLES.contains(role.getRoleName().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Built-in role '" + role.getRoleName() + "' cannot be deleted");
        }
        userRoleRepository.delete(role);
        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    "ROLE_DELETE",
                    "ROLE",
                    role.getId() != null ? role.getId().toString() : null,
                    "Deleted role: " + role.getRoleName(),
                    "SUCCESS"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getPermissionName))
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermission(String idOrName) {
        return PermissionResponse.from(findPermission(idOrName));
    }

    @Transactional
    public PermissionResponse createPermission(PermissionRequest request, Authentication authentication) {
        String permName = request.permissionName() != null
                ? request.permissionName().trim().toUpperCase(Locale.ROOT)
                : "";
        if (permName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission name cannot be blank");
        }
        if (permissionRepository.existsByPermissionNameIgnoreCase(permName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Permission with name '" + permName + "' already exists");
        }

        Permission permission = new Permission();
        permission.setPermissionName(permName);
        permission.setDescription(request.description() != null ? request.description().trim() : null);

        Permission saved = permissionRepository.save(permission);
        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    "PERMISSION_CREATE",
                    "PERMISSION",
                    saved.getId() != null ? saved.getId().toString() : null,
                    "Created permission: " + saved.getPermissionName(),
                    "SUCCESS"
            );
        }
        return PermissionResponse.from(saved);
    }

    @Transactional
    public PermissionResponse updatePermission(String idOrName, PermissionRequest request, Authentication authentication) {
        Permission permission = findPermission(idOrName);
        if (request.description() != null) {
            permission.setDescription(request.description().trim());
        }
        Permission saved = permissionRepository.save(permission);
        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    "PERMISSION_UPDATE",
                    "PERMISSION",
                    saved.getId() != null ? saved.getId().toString() : null,
                    "Updated permission: " + saved.getPermissionName(),
                    "SUCCESS"
            );
        }
        return PermissionResponse.from(saved);
    }

    @Transactional
    public void deletePermission(String idOrName, Authentication authentication) {
        Permission permission = findPermission(idOrName);
        permissionRepository.delete(permission);
        if (auditLogService != null) {
            auditLogService.logAction(
                    authentication,
                    "PERMISSION_DELETE",
                    "PERMISSION",
                    permission.getId() != null ? permission.getId().toString() : null,
                    "Deleted permission: " + permission.getPermissionName(),
                    "SUCCESS"
            );
        }
    }

    public UserRole findRole(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role identifier is required");
        }
        try {
            UUID uuid = UUID.fromString(idOrName.trim());
            return userRoleRepository.findById(uuid)
                    .orElseGet(() -> userRoleRepository.findByRoleNameIgnoreCase(idOrName.trim())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found")));
        } catch (IllegalArgumentException ex) {
            return userRoleRepository.findByRoleNameIgnoreCase(idOrName.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        }
    }

    public Permission findPermission(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission identifier is required");
        }
        try {
            UUID uuid = UUID.fromString(idOrName.trim());
            return permissionRepository.findById(uuid)
                    .orElseGet(() -> permissionRepository.findByPermissionNameIgnoreCase(idOrName.trim())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found")));
        } catch (IllegalArgumentException ex) {
            return permissionRepository.findByPermissionNameIgnoreCase(idOrName.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission not found"));
        }
    }

    public Set<Permission> resolvePermissions(Set<String> permissionNames) {
        if (permissionNames == null || permissionNames.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> perms = new HashSet<>();
        for (String permName : permissionNames) {
            if (permName == null || permName.isBlank()) continue;
            Permission p = permissionRepository.findByPermissionNameIgnoreCase(permName.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Permission '" + permName + "' does not exist"));
            perms.add(p);
        }
        return perms;
    }

    public Set<UserRole> resolveRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new HashSet<>();
        }
        Set<UserRole> roles = new HashSet<>();
        for (String roleName : roleNames) {
            if (roleName == null || roleName.isBlank()) continue;
            UserRole r = userRoleRepository.findByRoleNameIgnoreCase(roleName.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Role '" + roleName + "' does not exist"));
            roles.add(r);
        }
        return roles;
    }
}
