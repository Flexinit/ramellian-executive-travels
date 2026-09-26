package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Permission;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

public record UserResponse(UUID id,
                           String email,
                           String firstName,
                           String middleName,
                           String lastName,
                           boolean enabled,
                           List<String> roles,
                           List<String> permissions,
                           List<String> directPermissions,
                           String ownerType,
                           String businessName,
                           String businessRegistrationNumber,
                           String businessTaxNumber,
                           String businessAddress,
                           String payoutProvider,
                           String payoutStatus,
                           boolean isCarOwner,
                           boolean isCarRenter,
                           boolean hasAddedVehicleToProfile,
                           boolean hasCompletedProfile,
                           Double rating,
                           String location,
                           boolean emailVerified,
                           boolean twoFactorEnabled,
                           String idNumber,
                           String idNumberUrlFront,
                           String idNumberUrlBack,
                           String drivingLicenseFrontUrl,
                           String drivingLicenseBackUrl,
                           String phoneNumber,
                           String profilePhotoUrl,
                           String twoFactorMethod
) {
    public static UserResponse from(User user) {
        Set<String> allPermissions = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().stream()
                    .filter(r -> r.getPermissions() != null)
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getPermissionName)
                    .forEach(allPermissions::add);
        }
        List<String> directPerms = user.getDirectPermissions() != null
                ? user.getDirectPermissions().stream().map(Permission::getPermissionName).collect(Collectors.toList())
                : List.of();
        allPermissions.addAll(directPerms);

        return new UserResponse(
                user.getId(),
                user.getEmailAddress(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.isEnabled(),
                user.getRoles() != null ? user.getRoles().stream().map(UserRole::getRoleName).collect(Collectors.toList()) : List.of(),
                allPermissions.stream().toList(),
                directPerms,
                user.getOwnerType(),
                user.getBusinessName(),
                user.getBusinessRegistrationNumber(),
                user.getBusinessTaxNumber(),
                user.getBusinessAddress(),
                user.getPayoutProvider(),
                user.getPayoutStatus(),
                user.isCarOwner(),
                user.isCarRenter(),
                user.isAddedVehicleToProfile(),
                user.isCompletedProfile(),
                user.getRating(),
                user.getLocation(),
                user.isEmailVerified(),
                user.isTwoFactorEnabled(),
                user.getIdNumber(),
                user.getIdNumberUrlFront(),
                user.getIdNumberUrlBack(),
                user.getDrivingLicenseFrontUrl(),
                user.getDrivingLicenseBackUrl(),
                user.getPhoneNumber(),
                user.getProfilePhotoUrl(),
                user.getTwoFactorMethod()
        );
    }
}