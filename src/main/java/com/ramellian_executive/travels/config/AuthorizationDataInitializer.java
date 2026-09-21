package com.ramellian_executive.travels.config;

import com.ramellian_executive.travels.entity.Permission;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.repository.PermissionRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthorizationDataInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, Permission> permissions = new java.util.HashMap<>();
        permissionDefinitions().forEach(definition -> {
            Permission permission = permissionRepository.findByPermissionName(definition[0])
                    .orElseGet(Permission::new);
            permission.setPermissionName(definition[0]);
            permission.setDescription(definition[1]);
            permissions.put(definition[0], permissionRepository.save(permission));
        });

        rolePermissions().forEach((roleName, permissionNames) -> {
            UserRole role = userRoleRepository.findByRoleName(roleName)
                    .orElseGet(UserRole::new);
            role.setRoleName(roleName);
            role.setDescription(roleDescription(roleName));
            role.getPermissions().clear();
            permissionNames.stream()
                    .map(permissions::get)
                    .forEach(role.getPermissions()::add);
            userRoleRepository.save(role);
        });
    }

    private Map<String, Set<String>> rolePermissions() {
        Set<String> all = new HashSet<>(permissions().keySet());
        Map<String, Set<String>> result = new java.util.HashMap<>();
        result.put(UserRole.RENTER, Set.of(
                Permission.PROFILE_READ, Permission.BOOKING_CREATE,
                Permission.BOOKING_READ, Permission.PAYMENT_CREATE, Permission.PAYMENT_READ,
                Permission.REVIEW_CREATE, Permission.REVIEW_READ,
                Permission.DOCUMENT_UPLOAD, Permission.DOCUMENT_READ));
        result.put(UserRole.OWNER, Set.of(
                Permission.PROFILE_READ, Permission.VEHICLE_READ,
                Permission.VEHICLE_CREATE, Permission.VEHICLE_UPDATE,
                Permission.BOOKING_READ, Permission.BOOKING_UPDATE, Permission.DOCUMENT_UPLOAD,
                Permission.DOCUMENT_READ, Permission.PAYMENT_READ));
        result.put(UserRole.CHAUFFEUR, Set.of(
                Permission.PROFILE_READ, Permission.BOOKING_READ,
                Permission.BOOKING_UPDATE));
        result.put(UserRole.ADMIN, Set.of(
                Permission.PROFILE_READ, Permission.BOOKING_MANAGE,
                Permission.PAYMENT_READ, Permission.PAYMENT_MANAGE, Permission.PAYMENT_DELETE,
                Permission.VEHICLE_MANAGE, Permission.VEHICLE_DELETE,
                Permission.BOOKING_DELETE, Permission.USER_MANAGE, Permission.USER_DELETE,
                Permission.DOCUMENT_READ, Permission.DOCUMENT_REVIEW));
        result.put(UserRole.SUPER_ADMIN, all);
        return result;
    }

    private Map<String, String> permissions() {
        Map<String, String> permissions = new java.util.HashMap<>();
        permissionDefinitions().forEach(definition -> permissions.put(definition[0], definition[1]));
        return permissions;
    }

    private String roleDescription(String roleName) {
        return switch (roleName) {
            case UserRole.CHAUFFEUR -> "Driver who fulfills bookings";
            case UserRole.ADMIN -> "Administrative operator";
            case UserRole.SUPER_ADMIN -> "Full system administrator";
            case UserRole.OWNER -> "Vehicle owner";
            default -> "User who rents vehicles";
        };
    }

    private Set<String[]> permissionDefinitions() {
        return Set.of(
                new String[]{Permission.PROFILE_READ, "Read own profile"},
                new String[]{Permission.BOOKING_CREATE, "Create bookings"},
                new String[]{Permission.BOOKING_READ, "Read bookings"},
                new String[]{Permission.BOOKING_UPDATE, "Update assigned bookings"},
                new String[]{Permission.BOOKING_MANAGE, "Manage all bookings"},
                new String[]{Permission.BOOKING_DELETE, "Delete bookings"},
                new String[]{Permission.PAYMENT_CREATE, "Create payments"},
                new String[]{Permission.PAYMENT_READ, "Read payment status"},
                new String[]{Permission.PAYMENT_MANAGE, "Manage payments"},
                new String[]{Permission.PAYMENT_DELETE, "Delete payments"},
                new String[]{Permission.VEHICLE_READ, "Read vehicles"},
                new String[]{Permission.VEHICLE_CREATE, "Create vehicles"},
                new String[]{Permission.VEHICLE_UPDATE, "Update owned vehicles"},
                new String[]{Permission.VEHICLE_MANAGE, "Manage all vehicles"},
                new String[]{Permission.VEHICLE_DELETE, "Delete vehicles"},
                new String[]{Permission.REVIEW_CREATE, "Create vehicle reviews"},
                new String[]{Permission.REVIEW_READ, "Read vehicle reviews"},
                new String[]{Permission.USER_MANAGE, "Manage users"},
                new String[]{Permission.USER_DELETE, "Delete users"},
                new String[]{Permission.ROLE_MANAGE, "Manage roles and permissions"},
                new String[]{Permission.DOCUMENT_UPLOAD, "Upload identity and vehicle documents"},
                new String[]{Permission.DOCUMENT_READ, "Read own document metadata"},
                new String[]{Permission.DOCUMENT_REVIEW, "Approve or reject documents"});
    }
}
