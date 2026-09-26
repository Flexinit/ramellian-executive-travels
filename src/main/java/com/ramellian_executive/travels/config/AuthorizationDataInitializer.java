package com.ramellian_executive.travels.config;

import com.ramellian_executive.travels.entity.Permission;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.repository.PermissionRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.superadmin.email:superadmin@ramellian.com}")
    private String superAdminEmail;

    @Value("${app.superadmin.password:SuperAdmin@12345}")
    private String superAdminPassword;

    @Value("${app.superadmin.phone-number:+254700000000}")
    private String superAdminPhone;

    @Value("${app.superadmin.first-name:Super}")
    private String superAdminFirstName;

    @Value("${app.superadmin.last-name:Admin}")
    private String superAdminLastName;

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

        seedSuperAdmin();
    }

    private void seedSuperAdmin() {
        String adminEmail = (superAdminEmail != null && !superAdminEmail.isBlank())
                ? superAdminEmail.trim().toLowerCase()
                : "superadmin@ramellian.com";

        User superAdmin = userRepository.findByEmailAddressIgnoreCase(adminEmail)
                .orElseGet(User::new);

        superAdmin.setEmailAddress(adminEmail);
        if (superAdmin.getId() == null || superAdmin.getPassword() == null) {
            superAdmin.setPassword(passwordEncoder.encode(
                    (superAdminPassword != null && !superAdminPassword.isBlank()) ? superAdminPassword : "SuperAdmin@12345"));
        }
        if (superAdmin.getFirstName() == null || superAdmin.getFirstName().isBlank()) {
            superAdmin.setFirstName((superAdminFirstName != null && !superAdminFirstName.isBlank()) ? superAdminFirstName : "Super");
        }
        if (superAdmin.getLastName() == null || superAdmin.getLastName().isBlank()) {
            superAdmin.setLastName((superAdminLastName != null && !superAdminLastName.isBlank()) ? superAdminLastName : "Admin");
        }
        if (superAdmin.getPhoneNumber() == null || superAdmin.getPhoneNumber().isBlank()) {
            superAdmin.setPhoneNumber((superAdminPhone != null && !superAdminPhone.isBlank()) ? superAdminPhone : "+254700000000");
        }
        if (superAdmin.getIdNumber() == null || superAdmin.getIdNumber().isBlank()) {
            superAdmin.setIdNumber("SUPERADMIN001");
        }
        superAdmin.setEnabled(true);
        superAdmin.setEmailVerified(true);
        superAdmin.setCompletedProfile(true);

        UserRole superAdminRole = userRoleRepository.findByRoleName(UserRole.SUPER_ADMIN).orElse(null);
        if (superAdminRole != null) {
            superAdmin.getRoles().add(superAdminRole);
        }
        UserRole adminRole = userRoleRepository.findByRoleName(UserRole.ADMIN).orElse(null);
        if (adminRole != null) {
            superAdmin.getRoles().add(adminRole);
        }

        userRepository.save(superAdmin);
    }

    private Map<String, Set<String>> rolePermissions() {
        Set<String> all = new HashSet<>(permissions().keySet());
        Map<String, Set<String>> result = new java.util.HashMap<>();
        result.put(UserRole.CAR_RENTER, Set.of(
                Permission.PROFILE_READ, Permission.BOOKING_CREATE,
                Permission.BOOKING_READ, Permission.PAYMENT_CREATE, Permission.PAYMENT_READ,
                Permission.REVIEW_CREATE, Permission.REVIEW_READ,
                Permission.DOCUMENT_UPLOAD, Permission.DOCUMENT_READ));
        result.put(UserRole.CAR_OWNER, Set.of(
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
                Permission.VEHICLE_REVIEW, Permission.VEHICLE_APPROVE,
                Permission.BOOKING_DELETE, Permission.USER_MANAGE, Permission.USER_DELETE,
                Permission.ROLE_READ, Permission.ROLE_MANAGE, Permission.ROLE_CREATE, Permission.ROLE_UPDATE, Permission.ROLE_DELETE,
                Permission.PERMISSION_READ, Permission.PERMISSION_MANAGE,
                Permission.ANNOUNCEMENT_CREATE, Permission.ANNOUNCEMENT_READ, Permission.ANNOUNCEMENT_MANAGE, Permission.ANNOUNCEMENT_DELETE,
                Permission.DOCUMENT_READ, Permission.DOCUMENT_REVIEW,
                Permission.AUDIT_READ));
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
            case UserRole.CAR_OWNER -> "Vehicle owner";
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
                new String[]{Permission.VEHICLE_REVIEW, "Review and approve/reject vehicles"},
                new String[]{Permission.VEHICLE_APPROVE, "Approve or reject vehicles"},
                new String[]{Permission.REVIEW_CREATE, "Create vehicle reviews"},
                new String[]{Permission.REVIEW_READ, "Read vehicle reviews"},
                new String[]{Permission.USER_MANAGE, "Manage users"},
                new String[]{Permission.USER_DELETE, "Delete users"},
                new String[]{Permission.ROLE_READ, "Read system roles and permissions"},
                new String[]{Permission.ROLE_MANAGE, "Manage roles and permissions"},
                new String[]{Permission.ROLE_CREATE, "Create system roles"},
                new String[]{Permission.ROLE_UPDATE, "Update system roles and role permissions"},
                new String[]{Permission.ROLE_DELETE, "Delete system roles"},
                new String[]{Permission.PERMISSION_READ, "Read available system permissions"},
                new String[]{Permission.PERMISSION_MANAGE, "Manage system permissions"},
                new String[]{Permission.ANNOUNCEMENT_CREATE, "Create and publish announcements"},
                new String[]{Permission.ANNOUNCEMENT_READ, "Read system announcements"},
                new String[]{Permission.ANNOUNCEMENT_MANAGE, "Manage announcements and messaging"},
                new String[]{Permission.ANNOUNCEMENT_DELETE, "Delete announcements"},
                new String[]{Permission.DOCUMENT_UPLOAD, "Upload identity and vehicle documents"},
                new String[]{Permission.DOCUMENT_READ, "Read own document metadata"},
                new String[]{Permission.DOCUMENT_REVIEW, "Approve or reject documents"},
                new String[]{Permission.AUDIT_READ, "Read system trail and activity logs"});
    }
}
