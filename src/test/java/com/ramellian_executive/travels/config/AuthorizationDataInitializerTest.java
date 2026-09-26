package com.ramellian_executive.travels.config;

import com.ramellian_executive.travels.entity.Permission;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.repository.PermissionRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationDataInitializerTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthorizationDataInitializer initializer;

    @Test
    void runSeedsPermissionsRolesAndSuperAdmin() {
        when(permissionRepository.findByPermissionName(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(userRoleRepository.findByRoleName(anyString())).thenAnswer(invocation -> {
            UserRole role = new UserRole();
            role.setRoleName(invocation.getArgument(0));
            return Optional.of(role);
        });
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(userRepository.findByEmailAddressIgnoreCase("superadmin@ramellian.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.setField(initializer, "superAdminEmail", "superadmin@ramellian.com");
        ReflectionTestUtils.setField(initializer, "superAdminPassword", "SuperAdmin@12345");
        ReflectionTestUtils.setField(initializer, "superAdminPhone", "+254700000000");
        ReflectionTestUtils.setField(initializer, "superAdminFirstName", "Super");
        ReflectionTestUtils.setField(initializer, "superAdminLastName", "Admin");

        initializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("superadmin@ramellian.com", savedUser.getEmailAddress());
        assertEquals("encodedPassword123", savedUser.getPassword());
        assertEquals("Super", savedUser.getFirstName());
        assertEquals("Admin", savedUser.getLastName());
        assertEquals("+254700000000", savedUser.getPhoneNumber());
        assertTrue(savedUser.isEnabled());
        assertTrue(savedUser.isEmailVerified());
        assertTrue(savedUser.isCompletedProfile());
        assertTrue(savedUser.getRoles().stream().anyMatch(r -> UserRole.SUPER_ADMIN.equals(r.getRoleName())));
        assertTrue(savedUser.getRoles().stream().anyMatch(r -> UserRole.ADMIN.equals(r.getRoleName())));
    }
}
