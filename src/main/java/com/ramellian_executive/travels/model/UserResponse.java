package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

public record UserResponse(UUID id, String email, String firstName, String lastName,
                           boolean enabled, List<String> roles, String ownerType,
                           String businessName, String businessRegistrationNumber,
                           String businessTaxNumber, String businessAddress,
                           String payoutProvider, String payoutStatus) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmailAddress(), user.getFirstName(),
                user.getLastName(), user.isEnabled(),
                user.getRoles().stream().map(UserRole::getRoleName).collect(Collectors.toList()),
                user.getOwnerType(), user.getBusinessName(),
                user.getBusinessRegistrationNumber(), user.getBusinessTaxNumber(),
                user.getBusinessAddress(), user.getPayoutProvider(), user.getPayoutStatus());
    }
}