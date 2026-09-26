package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.ProfileRequest;
import com.ramellian_executive.travels.model.UserResponse;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock AuthenticationManager authenticationManager;
    @Mock PasswordEncoder passwordEncoder;
    @Mock Authentication authentication;

    @InjectMocks UserService userService;

    @Test
    void updateProfileUpdatesAllReflectedFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("john@example.com");
        user.setFirstName("OldFirst");
        user.setLastName("OldLast");
        user.setPhoneNumber("0700000000");

        when(authentication.getName()).thenReturn("john@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("john@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileRequest request = new ProfileRequest(
                "Jane",
                "Middle",
                "Doe",
                "0711223344",
                "Nairobi, Kenya",
                "12345678",
                "https://cdn.example.com/id-front.jpg",
                "https://cdn.example.com/id-back.jpg",
                "https://cdn.example.com/profile.jpg",
                "https://cdn.example.com/dl-front.jpg",
                "https://cdn.example.com/dl-back.jpg"
        );

        UserResponse response = userService.updateProfile(authentication, request);

        verify(userRepository).save(user);
        assertEquals("Jane", response.firstName());
        assertEquals("Middle", response.middleName());
        assertEquals("Doe", response.lastName());
        assertEquals("0711223344", response.phoneNumber());
        assertEquals("Nairobi, Kenya", response.location());
        assertEquals("12345678", response.idNumber());
        assertEquals("https://cdn.example.com/id-front.jpg", response.idNumberUrlFront());
        assertEquals("https://cdn.example.com/id-back.jpg", response.idNumberUrlBack());
        assertEquals("https://cdn.example.com/profile.jpg", response.profilePhotoUrl());
        assertEquals("https://cdn.example.com/dl-front.jpg", response.drivingLicenseFrontUrl());
        assertEquals("https://cdn.example.com/dl-back.jpg", response.drivingLicenseBackUrl());
    }

    @Test
    void updateProfilePartialUpdateKeepsExistingFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("john@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhoneNumber("0700000000");
        user.setIdNumber("87654321");

        when(authentication.getName()).thenReturn("john@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("john@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileRequest request = new ProfileRequest(
                null,
                null,
                null,
                null,
                "Mombasa, Kenya",
                null,
                null,
                null,
                "https://cdn.example.com/avatar.png",
                null,
                null
        );

        UserResponse response = userService.updateProfile(authentication, request);

        verify(userRepository).save(user);
        assertEquals("John", response.firstName());
        assertEquals("Doe", response.lastName());
        assertEquals("0700000000", response.phoneNumber());
        assertEquals("Mombasa, Kenya", response.location());
        assertEquals("87654321", response.idNumber());
        assertEquals("https://cdn.example.com/avatar.png", response.profilePhotoUrl());
    }

    @Test
    void updateOwnerProfileUpdatesOwnerDetailsAndRole() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("owner@example.com");

        UserRole ownerRole = new UserRole();
        ownerRole.setRoleName(UserRole.CAR_OWNER);

        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("owner@example.com")).thenReturn(Optional.of(user));
        when(userRoleRepository.findByRoleName(UserRole.CAR_OWNER)).thenReturn(Optional.of(ownerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.ramellian_executive.travels.model.OwnerProfileRequest request =
                new com.ramellian_executive.travels.model.OwnerProfileRequest(
                        "BUSINESS",
                        "Executive Fleet Ltd",
                        "REG123456",
                        "TAX987654",
                        "Nairobi CBD",
                        "MPESA",
                        "254700000000"
                );

        UserResponse response = userService.updateOwnerProfile(authentication, request);

        verify(userRepository).save(user);
        assertEquals("BUSINESS", response.ownerType());
        assertEquals("Executive Fleet Ltd", response.businessName());
        assertEquals("REG123456", response.businessRegistrationNumber());
        assertEquals("TAX987654", response.businessTaxNumber());
        assertEquals("Nairobi CBD", response.businessAddress());
        assertEquals("MPESA", response.payoutProvider());
        assertEquals("PENDING_VERIFICATION", response.payoutStatus());
        org.junit.jupiter.api.Assertions.assertTrue(response.isCarOwner());
        org.junit.jupiter.api.Assertions.assertTrue(response.roles().contains(UserRole.CAR_OWNER));
    }

    @Test
    void updateProfileWorksWithZonedDateTimeTimestamps() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("john@example.com");
        user.setCreatedAt(ZonedDateTime.now());
        user.setLastModifiedAt(ZonedDateTime.now());
        user.setUpdatedAt(ZonedDateTime.now());

        when(authentication.getName()).thenReturn("john@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("john@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileRequest request = new ProfileRequest(
                "Jane",
                null,
                "Doe",
                "0711223344",
                "Nairobi, Kenya",
                "12345678",
                "https://cdn.example.com/id-front.jpg",
                "https://cdn.example.com/id-back.jpg",
                "https://cdn.example.com/profile.jpg",
                null,
                null
        );

        UserResponse response = userService.updateProfile(authentication, request);

        assertNotNull(response);
        assertTrue(response.hasCompletedProfile());
    }

    @Test
    void userHasCompletedProfileChecksMandatoryFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("user@example.com");
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPhoneNumber("0712345678");
        user.setLocation("Nairobi");
        user.setIdNumber("12345678");
        user.setIdNumberUrlFront("front.jpg");
        user.setIdNumberUrlBack("back.jpg");
        assertFalse(userService.userHasCompletedProfile(user));

        user.setProfilePhotoUrl("profile.jpg");
        assertTrue(userService.userHasCompletedProfile(user));

        user.setCarOwner(true);
        assertFalse(userService.userHasCompletedProfile(user));

        user.setOwnerType("INDIVIDUAL");
        user.setPayoutProvider("MPESA");
        user.setPayoutAccountReference("0712345678");
        assertTrue(userService.userHasCompletedProfile(user));
    }

    @Test
    void userHasCompletedProfileByUserIdDelegates() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmailAddress("user@example.com");
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPhoneNumber("0712345678");
        user.setLocation("Nairobi");
        user.setIdNumber("12345678");
        user.setIdNumberUrlFront("front.jpg");
        user.setIdNumberUrlBack("back.jpg");
        user.setProfilePhotoUrl("profile.jpg");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertTrue(userService.userHasCompletedProfile(userId));
    }
}
