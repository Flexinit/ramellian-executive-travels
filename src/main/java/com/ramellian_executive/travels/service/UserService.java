package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import com.ramellian_executive.travels.support.Constants;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository, AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder) {
        this(userRepository, userRoleRepository, authenticationManager, passwordEncoder, null);
    }

    @Autowired
    public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository, AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder, @Autowired(required = false) AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserResponse getUserDetails(Authentication authentication) {
        User user = findUser(authentication.getName());
        return UserResponse.from(user);
    }


    public UserResponse updateProfile(Authentication authentication, ProfileRequest request) {
        User user = findUser(authentication.getName());
        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.middleName() != null) {
            user.setMiddleName(request.middleName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }
        if (request.location() != null) {
            user.setLocation(request.location().trim());
        }
        if (request.idNumber() != null) {
            user.setIdNumber(request.idNumber().trim());
        }
        if (request.idNumberUrlFront() != null) {
            user.setIdNumberUrlFront(request.idNumberUrlFront().trim());
        }
        if (request.idNumberUrlBack() != null) {
            user.setIdNumberUrlBack(request.idNumberUrlBack().trim());
        }
        if (request.drivingLicenseFrontUrl() != null) {
            user.setDrivingLicenseFrontUrl(request.drivingLicenseFrontUrl().trim());
        }
        if (request.drivingLicenseBackUrl() != null) {
            user.setDrivingLicenseBackUrl(request.drivingLicenseBackUrl().trim());
        }
        if (request.drivingLicenceNumber() != null) {
            user.setDrivingLicenceNumber(request.drivingLicenceNumber().trim());
        }
        if (request.profilePhotoUrl() != null) {
            user.setProfilePhotoUrl(request.profilePhotoUrl().trim());
        }
        user.setCompletedProfile(userHasCompletedProfile(user));
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse updateOwnerProfile(Authentication authentication, OwnerProfileRequest request) {
        User user = findUser(authentication.getName());
        String ownerType = request.ownerType() != null && !request.ownerType().isBlank()
                ? request.ownerType().trim().toUpperCase()
                : user.getOwnerType();

        if (ownerType != null && !Constants.INDIVIDUAL.equals(ownerType) && !Constants.BUSINESS.equals(ownerType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Owner type must be INDIVIDUAL or BUSINESS");
        }

        if (Constants.BUSINESS.equals(ownerType)) {
            String businessName = request.businessName() != null ? request.businessName() : user.getBusinessName();
            String regNumber = request.businessRegistrationNumber() != null ? request.businessRegistrationNumber() : user.getBusinessRegistrationNumber();
            if (businessName == null || businessName.isBlank() || regNumber == null || regNumber.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Business name and registration number are required for business owners");
            }
        }

        if (ownerType != null) {
            user.setOwnerType(ownerType);
        }
        user.setCarOwner(true);

        if (request.businessName() != null) {
            user.setBusinessName(request.businessName().trim());
        }
        if (request.businessRegistrationNumber() != null) {
            user.setBusinessRegistrationNumber(request.businessRegistrationNumber().trim());
        }
        if (request.businessTaxNumber() != null) {
            user.setBusinessTaxNumber(request.businessTaxNumber().trim());
        }
        if (request.businessAddress() != null) {
            user.setBusinessAddress(request.businessAddress().trim());
        }
        if (request.payoutProvider() != null) {
            user.setPayoutProvider(request.payoutProvider().trim());
        }
        if (request.payoutAccountReference() != null) {
            user.setPayoutAccountReference(request.payoutAccountReference().trim());
        }

        String provider = user.getPayoutProvider();
        String ref = user.getPayoutAccountReference();
        user.setPayoutStatus(provider == null || provider.isBlank() || ref == null || ref.isBlank()
                ? Constants.NOT_CONFIGURED : Constants.PENDING_VERIFICATION);

        userRoleRepository.findByRoleName(UserRole.CAR_OWNER).ifPresent(role -> {
            if (!user.getRoles().contains(role)) {
                user.getRoles().add(role);
            }
        });

        user.setCompletedProfile(userHasCompletedProfile(user));
        return UserResponse.from(userRepository.save(user));
    }


    public boolean userHasCompletedProfile(UUID userId) {
        return userHasCompletedProfile(findUser(userId));
    }

    public boolean userHasCompletedProfile(User user) {
        if (user == null) {
            return false;
        }

        if (isBlank(user.getFirstName())
                || isBlank(user.getLastName())
                || isBlank(user.getEmailAddress())
                || isBlank(user.getPhoneNumber())
                || isBlank(user.getLocation())
                || isBlank(user.getIdNumber())
                || isBlank(user.getIdNumberUrlFront())
                || isBlank(user.getIdNumberUrlBack())
                || isBlank(user.getProfilePhotoUrl())) {
            return false;
        }

        if (user.isCarOwner()) {
            if (isBlank(user.getOwnerType())
                    || isBlank(user.getPayoutProvider())
                    || isBlank(user.getPayoutAccountReference())) {
                return false;
            }
            if (Constants.BUSINESS.equalsIgnoreCase(user.getOwnerType())) {
                if (isBlank(user.getBusinessName()) || isBlank(user.getBusinessRegistrationNumber())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
        if (Constants.WHATSAPP.equalsIgnoreCase(request.method())
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
        User saved = userRepository.save(user);
        if (auditLogService != null) {
            auditLogService.logAction(user.getEmailAddress(), "USER_STATUS_CHANGE", "USER", user.getId() != null ? user.getId().toString() : null, "User status updated to enabled=" + request.enabled(), "SUCCESS");
        }
        return UserResponse.from(saved);
    }


    @Transactional
    public UserResponse setRole(UUID id, RoleRequest request) {
        User user = findUser(id);
        UserRole role = userRoleRepository.findByRoleName(request.role().trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        user.getRoles().clear();
        user.getRoles().add(role);
        User saved = userRepository.save(user);
        if (auditLogService != null) {
            auditLogService.logAction(user.getEmailAddress(), "USER_ROLE_CHANGE", "USER", user.getId() != null ? user.getId().toString() : null, "User role set to " + request.role(), "SUCCESS");
        }
        return UserResponse.from(saved);
    }


    public void deleteUser( UUID id, Authentication authentication) {
        User user = findUser(id);
        if (user.getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
        }
        userRepository.delete(user);
        if (auditLogService != null) {
            auditLogService.logAction(authentication, "USER_DELETE", "USER", id.toString(), "User " + user.getEmailAddress() + " deleted", "SUCCESS");
        }
    }

    @Transactional
    public StaffRenterResponse createRenter(StaffRenterRequest request, Authentication authentication) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAddressIgnoreCase(email)) {
            User existing = userRepository.findByEmailAddressIgnoreCase(email).get();
            return new StaffRenterResponse(
                    existing.getId(), existing.getEmailAddress(),
                    existing.getFirstName(), existing.getLastName(),
                    null, true);
        }

        String tempPassword = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setEmailAddress(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCarRenter(true);

        UserRole role = userRoleRepository.findByRoleName(UserRole.CAR_RENTER)
                .orElseGet(() -> {
                    UserRole r = new UserRole();
                    r.setRoleName(UserRole.CAR_RENTER);
                    r.setDescription("User who rents vehicles");
                    return userRoleRepository.save(r);
                });
        user.getRoles().add(role);
        User saved = userRepository.save(user);

        if (auditLogService != null) {
            auditLogService.logAction(authentication, "STAFF_RENTER_CREATE", "USER",
                    saved.getId().toString(),
                    "Staff created renter account for " + email, "SUCCESS");
        }
        return new StaffRenterResponse(
                saved.getId(), saved.getEmailAddress(),
                saved.getFirstName(), saved.getLastName(),
                tempPassword, false);
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
