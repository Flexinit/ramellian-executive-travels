package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository, AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse currentUser(Authentication authentication) {
        User user = findUser(authentication.getName());
        return UserResponse.from(user);
    }


    public UserResponse updateProfile(Authentication authentication,  ProfileRequest request) {
        User user = findUser(authentication.getName());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhoneNumber(request.phoneNumber());
        user.setLocation(request.location());
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse updateOwnerProfile(Authentication authentication, OwnerProfileRequest request) {
        User user = findUser(authentication.getName());
        String ownerType = request.ownerType().trim().toUpperCase();
        if (!"INDIVIDUAL".equals(ownerType) && !"BUSINESS".equals(ownerType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Owner type must be INDIVIDUAL or BUSINESS");
        }
        if ("BUSINESS".equals(ownerType)
                && (request.businessName() == null || request.businessName().isBlank()
                || request.businessRegistrationNumber() == null
                || request.businessRegistrationNumber().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Business name and registration number are required for business owners");
        }
        user.setOwnerType(ownerType);
        user.setBusinessName(request.businessName());
        user.setBusinessRegistrationNumber(request.businessRegistrationNumber());
        user.setBusinessTaxNumber(request.businessTaxNumber());
        user.setBusinessAddress(request.businessAddress());
        user.setPayoutProvider(request.payoutProvider());
        user.setPayoutAccountReference(request.payoutAccountReference());
        user.setPayoutStatus(request.payoutProvider() == null || request.payoutProvider().isBlank()
                || request.payoutAccountReference() == null
                || request.payoutAccountReference().isBlank()
                ? "NOT_CONFIGURED" : "PENDING_VERIFICATION");
        userRoleRepository.findByRoleName(UserRole.OWNER).ifPresent(user.getRoles()::add);
        return UserResponse.from(userRepository.save(user));
    }


    public void changePassword(Authentication authentication, PasswordRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    authentication.getName(), request.currentPassword()));
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        User user = findUser(authentication.getName());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse configureTwoFactor(
            Authentication authentication,  TwoFactorRequest request) {
        User user = findUser(authentication.getName());
        if ("WHATSAPP".equalsIgnoreCase(request.method())
                && (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A phone number is required for WhatsApp two-factor authentication");
        }
        user.setTwoFactorEnabled(request.enabled());
        user.setTwoFactorMethod(request.method().trim().toUpperCase());
        return UserResponse.from(userRepository.save(user));
    }

   @Transactional
    public UserResponse getUser(UUID id) {
        return UserResponse.from(findUser(id));
    }


    public UserResponse setEnabled(UUID id, EnabledRequest request) {
        User user = findUser(id);
        user.setEnabled(request.enabled());
        return UserResponse.from(userRepository.save(user));
    }


    @Transactional
    public UserResponse setRole(UUID id, RoleRequest request) {
        User user = findUser(id);
        UserRole role = userRoleRepository.findByRoleName(request.role().trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        user.getRoles().clear();
        user.getRoles().add(role);
        return UserResponse.from(userRepository.save(user));
    }


    public void deleteUser( UUID id, Authentication authentication) {
        User user = findUser(id);
        if (user.getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
        }
        userRepository.delete(user);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmailAddressIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
