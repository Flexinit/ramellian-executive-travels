package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import com.ramellian_executive.travels.security.JwtService;
import com.ramellian_executive.travels.security.VerificationCodeService;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import com.ramellian_executive.travels.security.JwtUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UserDetailsService userDetailsService;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock VerificationCodeService verificationCodeService;
    @Mock VerificationNotificationService notificationService;
    @Mock UserDetails userDetails;

    @InjectMocks AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "codeExpirationMinutes", 15L);
    }

    @Test
    void registerCreatesUnverifiedRenterAndSendsVerificationCode() {
        UserRole renter = new UserRole();
        renter.setRoleName(UserRole.CAR_RENTER);
        when(userRepository.existsByEmailAddressIgnoreCase("person@example.com")).thenReturn(false);
        when(userRoleRepository.findByRoleName(UserRole.CAR_RENTER)).thenReturn(Optional.of(renter));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(verificationCodeService.generateCode()).thenReturn("123456");
        when(verificationCodeService.hash("123456")).thenReturn("hashed");

        ResponseEntity<AuthResponse> response = authService.register(
                new RegisterRequest("person@example.com", "password123", "First", "Last"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        verify(notificationService).sendEmail("person@example.com", "123456", "email verification");
        assertFalse(captor.getValue().isEmailVerified());
        assertTrue(captor.getValue().isCarRenter());
        assertFalse(captor.getValue().isCarOwner());
        assertTrue(captor.getValue().getRoles().contains(renter));
        assertEquals("Email verification required", response.getBody().accessToken());
    }

    @Test
    void registerWithWhatsAppChannelSendsWhatsAppCode() {
        UserRole renter = new UserRole();
        renter.setRoleName(UserRole.CAR_RENTER);
        when(userRepository.existsByEmailAddressIgnoreCase("person@example.com")).thenReturn(false);
        when(userRoleRepository.findByRoleName(UserRole.CAR_RENTER)).thenReturn(Optional.of(renter));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(verificationCodeService.generateCode()).thenReturn("123456");
        when(verificationCodeService.hash("123456")).thenReturn("hashed");

        RegisterRequest request = new RegisterRequest(
                "person@example.com", "password123", "First", "Last", "+254712345678", "WHATSAPP", null);
        ResponseEntity<AuthResponse> response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        verify(notificationService).sendWhatsApp("+254712345678", "123456");
        verify(notificationService, never()).sendEmail(anyString(), anyString(), anyString());
        assertEquals("+254712345678", captor.getValue().getPhoneNumber());
        assertTrue(captor.getValue().isCarRenter());
        assertEquals("WhatsApp verification code sent", response.getBody().accessToken());
    }

    @Test
    void sendWhatsAppVerificationUpdatesUserAndSendsCode() {
        User user = new User();
        user.setEmailAddress("person@example.com");
        user.setPhoneNumber("+254711111111");
        when(userRepository.findByEmailAddressIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeService.generateCode()).thenReturn("998877");
        when(verificationCodeService.hash("998877")).thenReturn("hashed-code");

        authService.sendWhatsAppVerification(new WhatsAppVerificationRequest("person@example.com", "+254722222222"));

        verify(userRepository).save(user);
        assertEquals("+254722222222", user.getPhoneNumber());
        assertEquals("hashed-code", user.getVerificationCodeHash());
        verify(notificationService).sendWhatsApp("+254722222222", "998877");
    }

    @Test
    void verifyEmailWithPhoneNumberVerifiesSuccessfully() {
        User user = new User();
        user.setPhoneNumber("+254712345678");
        user.setEmailVerified(false);
        user.setVerificationCodeHash("hashed-123456");
        user.setVerificationCodeExpiresAt(java.time.ZonedDateTime.now().plusMinutes(10));

        when(userRepository.findByPhoneNumber("+254712345678")).thenReturn(Optional.of(user));
        when(verificationCodeService.matches("123456", "hashed-123456")).thenReturn(true);

        authService.verifyEmail(new VerifyCodeRequest(null, "+254712345678", "123456"));

        assertTrue(user.isEmailVerified());
        assertNull(user.getVerificationCodeHash());
        assertNull(user.getVerificationCodeExpiresAt());
        verify(userRepository).save(user);
    }

    @Test
    void unverifiedUserIsRejectedBeforePasswordAuthentication() {
        User user = new User();
        user.setEmailAddress("person@example.com");
        user.setEmailVerified(false);
        when(userRepository.findByEmailAddressIgnoreCase("person@example.com")).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("person@example.com", "password123")));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginReturnsTokenAfterVerifiedAuthentication() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("person@example.com");
        user.setEmailVerified(true);
        when(userRepository.findByEmailAddressIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("person@example.com")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("jwt");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = authService.login(new LoginRequest("person@example.com", "password123"));

        verify(authenticationManager).authenticate(any());
        assertEquals("jwt", response.accessToken());
        assertEquals("Bearer", response.tokenType());
    }

    @Test
    void jwtUserPrincipalIncludesUserIdentityInClaims() {
        UUID userId = UUID.randomUUID();
        JwtUserPrincipal principal = new JwtUserPrincipal(userId, "person@example.com", "encoded", Set.of());

        assertEquals(userId, principal.getUserId());
        assertEquals("person@example.com", principal.getEmail());
        assertEquals("person@example.com", principal.getUsername());
    }

    @Test
    void invalidCredentialsAreRejected() {
        User user = new User();
        user.setEmailAddress("person@example.com");
        user.setEmailVerified(true);
        when(userRepository.findByEmailAddressIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        doThrow(new BadCredentialsException("invalid")).when(authenticationManager).authenticate(any());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("person@example.com", "password123")));

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void resendVerificationReplacesCodeAndSendsEmail() {
        User user = new User();
        user.setEmailAddress("person@example.com");
        user.setEmailVerified(false);
        when(userRepository.findByEmailAddressIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeService.generateCode()).thenReturn("654321");
        when(verificationCodeService.hash("654321")).thenReturn("new-hash");

        authService.resendVerification(new ResendVerificationRequest("person@example.com"));

        assertEquals("new-hash", user.getVerificationCodeHash());
        verify(notificationService).sendEmail("person@example.com", "654321", "email verification");
    }
}
