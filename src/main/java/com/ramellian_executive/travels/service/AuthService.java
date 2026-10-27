package com.ramellian_executive.travels.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.model.FirebasePhoneVerifyRequest;
import com.ramellian_executive.travels.model.ForgotPasswordRequest;
import com.ramellian_executive.travels.model.ResetPasswordRequest;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import com.ramellian_executive.travels.security.GoogleIdTokenVerifierService;
import com.ramellian_executive.travels.security.JwtService;
import com.ramellian_executive.travels.entity.RefreshToken;
import com.ramellian_executive.travels.security.VerificationCodeService;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;

    private final UserDetailsService userDetailsService;

    private final UserRepository userRepository;

    private final UserRoleRepository userRoleRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final VerificationCodeService verificationCodeService;

    private final VerificationNotificationService notificationService;

    private final GoogleIdTokenVerifierService googleIdTokenVerifier;

    private final FirebaseService firebaseService;

    private final RefreshTokenService refreshTokenService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Value("${app.verification.code-expiration-minutes}")
    private long codeExpirationMinutes;


    @Transactional
    public ResponseEntity<AuthResponse> register(RegisterRequest request){
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailAddressIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email address is already registered");
        }

        User user = new User();
        user.setEmailAddress(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }
        user.setEnabled(true);
        user.setEmailVerified(false);

        String requestedRole = request.role() != null && !request.role().isBlank()
                ? request.role().trim().toUpperCase()
                : UserRole.CAR_RENTER;
        String targetRole = UserRole.CAR_OWNER.equals(requestedRole) ? UserRole.CAR_OWNER : UserRole.CAR_RENTER;
        if (UserRole.CAR_OWNER.equals(targetRole)) {
            user.setCarOwner(true);
            user.setCarRenter(false);
        } else {
            user.setCarRenter(true);
            user.setCarOwner(false);
        }

        UserRole role = userRoleRepository.findByRoleName(targetRole)
                .orElseGet(() -> {
                    UserRole r = new UserRole();
                    r.setRoleName(targetRole);
                    r.setDescription(UserRole.CAR_OWNER.equals(targetRole) ? "User who owns vehicles" : "User who rents vehicles");
                    return userRoleRepository.save(r);
                });
        user.getRoles().add(role);

        String code = verificationCodeService.generateCode();
        user.setVerificationCodeHash(verificationCodeService.hash(code));
        user.setVerificationCodeExpiresAt(ZonedDateTime.now().plusMinutes(codeExpirationMinutes));
        userRepository.save(user);

        if (auditLogService != null) {
            auditLogService.logAction(email, "AUTH_REGISTER", "USER", user.getId() != null ? user.getId().toString() : null, "User registered with role " + targetRole, "SUCCESS");
        }

        boolean useWhatsApp = "WHATSAPP".equalsIgnoreCase(request.verificationChannel());
        if (useWhatsApp && user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            notificationService.sendWhatsApp(user.getPhoneNumber(), code);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(null, "WhatsApp verification code sent", 0, null));
        } else {
            notificationService.sendEmail(email, code, "email verification");
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(null, "Email verification required", 0, null));
        }
    }

    @Transactional
    public void verifyEmail(VerifyCodeRequest request){
        User user = findUserByEmailOrPhone(request.email(), request.phoneNumber());
        if (user.isEmailVerified()
                || user.getVerificationCodeExpiresAt() == null
                || user.getVerificationCodeExpiresAt().isBefore(ZonedDateTime.now())
                || !verificationCodeService.matches(request.code(), user.getVerificationCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
        }
        user.setEmailVerified(true);
        user.setVerificationCodeHash(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);

        if (auditLogService != null) {
            auditLogService.logAction(user.getEmailAddress(), "AUTH_VERIFY_EMAIL", "USER", user.getId() != null ? user.getId().toString() : null, "Email verified successfully", "SUCCESS");
        }
    }


    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        User user = findUser(request.email());
        if (user.isEmailVerified()) {
            return;
        }
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            user.setPhoneNumber(request.phoneNumber().trim());
        }
        String code = verificationCodeService.generateCode();
        user.setVerificationCodeHash(verificationCodeService.hash(code));
        user.setVerificationCodeExpiresAt(ZonedDateTime.now().plusMinutes(codeExpirationMinutes));
        userRepository.save(user);

        if ("WHATSAPP".equalsIgnoreCase(request.channel())) {
            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required for WhatsApp verification");
            }
            notificationService.sendWhatsApp(user.getPhoneNumber(), code);
        } else {
            notificationService.sendEmail(user.getEmailAddress(), code, "email verification");
        }
    }

    @Transactional
    public void sendWhatsAppVerification(WhatsAppVerificationRequest request) {
        User user = findUserByEmailOrPhone(request.email(), request.phoneNumber());
        String phone = request.phoneNumber() != null && !request.phoneNumber().isBlank()
                ? request.phoneNumber().trim()
                : user.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required for WhatsApp verification");
        }
        user.setPhoneNumber(phone);
        String code = verificationCodeService.generateCode();
        user.setVerificationCodeHash(verificationCodeService.hash(code));
        user.setVerificationCodeExpiresAt(ZonedDateTime.now().plusMinutes(codeExpirationMinutes));
        userRepository.save(user);
        notificationService.sendWhatsApp(phone, code);
    }


    public AuthResponse login( LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = findUser(email);
        if ("GOOGLE".equalsIgnoreCase(user.getAuthProvider()) && user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "This account uses Google Sign-In and has no password set. " +
                    "Please sign in with Google, or use 'Forgot Password' to create a password for email login.");
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email verification is required");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException | DisabledException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (user.isTwoFactorEnabled()) {
            String code = verificationCodeService.generateCode();
            user.setTwoFactorCodeHash(verificationCodeService.hash(code));
            user.setTwoFactorCodeExpiresAt(ZonedDateTime.now().plusMinutes(codeExpirationMinutes));
            userRepository.save(user);
            deliverTwoFactorCode(user, code);
            if (auditLogService != null) {
                auditLogService.logAction(email, "AUTH_2FA_CHALLENGE", "USER", user.getId() != null ? user.getId().toString() : null, "2FA challenge code generated", "SUCCESS");
            }
            return new AuthResponse(null, "Two-factor verification required", 0, null);
        }
        if (auditLogService != null) {
            auditLogService.logAction(email, "AUTH_LOGIN", "USER", user.getId() != null ? user.getId().toString() : null, "User logged in successfully", "SUCCESS");
        }
        return tokenResponse(userDetailsService.loadUserByUsername(email), Boolean.TRUE.equals(request.rememberMe()));
    }


    @Transactional
    public AuthResponse verifyTwoFactor(VerifyCodeRequest request) {
        User user = findUser(request.email());
        if (user.getTwoFactorCodeExpiresAt() == null
                || user.getTwoFactorCodeExpiresAt().isBefore(ZonedDateTime.now())
                || !verificationCodeService.matches(request.code(), user.getTwoFactorCodeHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired two-factor code");
        }
        user.setTwoFactorCodeHash(null);
        user.setTwoFactorCodeExpiresAt(null);
        userRepository.save(user);

        if (auditLogService != null) {
            auditLogService.logAction(user.getEmailAddress(), "AUTH_2FA_VERIFY", "USER", user.getId() != null ? user.getId().toString() : null, "Two-factor verification successful", "SUCCESS");
        }

        return tokenResponse(userDetailsService.loadUserByUsername(user.getEmailAddress()), Boolean.TRUE.equals(request.rememberMe()));
    }

    @Transactional
    public AuthResponse googleSignIn(GoogleAuthRequest request) {
        GoogleIdToken.Payload payload = googleIdTokenVerifier.verify(request.idToken());

        String email = payload.getEmail().trim().toLowerCase();
        String googleId = payload.getSubject();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");
        String pictureUrl = (String) payload.get("picture");

        User user = userRepository.findByEmailAddressIgnoreCase(email)
                .or(() -> userRepository.findByGoogleId(googleId))
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmailAddress(email);
                    newUser.setGoogleId(googleId);
                    newUser.setAuthProvider("GOOGLE");
                    newUser.setFirstName(firstName != null ? firstName : "");
                    newUser.setLastName(lastName != null ? lastName : "");
                    newUser.setProfilePhotoUrl(pictureUrl);
                    newUser.setEnabled(true);
                    newUser.setEmailVerified(true);

                    String requestedRole = request.role() != null && !request.role().isBlank()
                            ? request.role().trim().toUpperCase()
                            : UserRole.CAR_RENTER;
                    String targetRole = UserRole.CAR_OWNER.equals(requestedRole) ? UserRole.CAR_OWNER : UserRole.CAR_RENTER;
                    if (UserRole.CAR_OWNER.equals(targetRole)) {
                        newUser.setCarOwner(true);
                        newUser.setCarRenter(false);
                    } else {
                        newUser.setCarRenter(true);
                        newUser.setCarOwner(false);
                    }

                    UserRole role = userRoleRepository.findByRoleName(targetRole)
                            .orElseGet(() -> {
                                UserRole r = new UserRole();
                                r.setRoleName(targetRole);
                                r.setDescription(UserRole.CAR_OWNER.equals(targetRole) ? "User who owns vehicles" : "User who rents vehicles");
                                return userRoleRepository.save(r);
                            });
                    newUser.getRoles().add(role);
                    return userRepository.save(newUser);
                });

        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            if (pictureUrl != null && user.getProfilePhotoUrl() == null) {
                user.setProfilePhotoUrl(pictureUrl);
            }
            userRepository.save(user);
        }

        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        if (auditLogService != null) {
            auditLogService.logAction(email, "AUTH_GOOGLE_SIGN_IN", "USER",
                    user.getId() != null ? user.getId().toString() : null,
                    "Google sign-in successful", "SUCCESS");
        }

        return tokenResponse(userDetailsService.loadUserByUsername(email));
    }

    @Transactional
    public void verifyFirebasePhone(FirebasePhoneVerifyRequest request, String authenticatedEmail) {
        String verifiedPhone = firebaseService.verifyPhoneToken(request.firebaseIdToken());

        User user;
        if (authenticatedEmail != null && !authenticatedEmail.isBlank()) {
            user = findUser(authenticatedEmail);
        } else if (request.email() != null && !request.email().isBlank()) {
            user = findUser(request.email().trim().toLowerCase());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide your email address or be logged in to verify your phone");
        }

        if (userRepository.findByPhoneNumber(verifiedPhone)
                .filter(existing -> !existing.getId().equals(user.getId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This phone number is already linked to another account");
        }

        user.setPhoneNumber(verifiedPhone);
        user.setPhoneVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        userRepository.findByEmailAddressIgnoreCase(email).ifPresent(user -> {
            String code = verificationCodeService.generateCode();
            user.setVerificationCodeHash(verificationCodeService.hash(code));
            user.setVerificationCodeExpiresAt(ZonedDateTime.now().plusMinutes(codeExpirationMinutes));
            userRepository.save(user);
            boolean isGoogleOnly = "GOOGLE".equalsIgnoreCase(user.getAuthProvider()) && user.getPassword() == null;
            if (isGoogleOnly) {
                notificationService.sendSetPasswordEmail(email, code);
            } else {
                notificationService.sendPasswordResetEmail(email, code);
            }
            if (auditLogService != null) {
                auditLogService.logAction(email, "AUTH_FORGOT_PASSWORD", "USER",
                        user.getId() != null ? user.getId().toString() : null,
                        isGoogleOnly ? "Set-password code generated for Google account" : "Password reset code generated", "SUCCESS");
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailAddressIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code"));
        if (user.getVerificationCodeExpiresAt() == null
                || user.getVerificationCodeExpiresAt().isBefore(ZonedDateTime.now())
                || !verificationCodeService.matches(request.code(), user.getVerificationCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setVerificationCodeHash(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);
        if (auditLogService != null) {
            auditLogService.logAction(email, "AUTH_RESET_PASSWORD", "USER",
                    user.getId() != null ? user.getId().toString() : null,
                    "Password reset successfully", "SUCCESS");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmailAddressIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }

    private User findUserByEmailOrPhone(String email, String phoneNumber) {
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmailAddressIgnoreCase(email.trim().toLowerCase())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            return userRepository.findByPhoneNumber(phoneNumber.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address or phone number is required");
    }

    private void deliverTwoFactorCode(User user, String code) {
        if ("WHATSAPP".equalsIgnoreCase(user.getTwoFactorMethod())) {
            notificationService.sendWhatsApp(user.getPhoneNumber(), code);
        } else {
            notificationService.sendEmail(user.getEmailAddress(), code, "two-factor authentication");
        }
    }

    private AuthResponse tokenResponse(UserDetails userDetails) {
        return tokenResponse(userDetails, false);
    }

    private AuthResponse tokenResponse(UserDetails userDetails, boolean rememberMe) {
        String accessToken = jwtService.generateToken(userDetails, rememberMe);
        long expiresIn = rememberMe ? jwtService.getRememberMeExpirationMs() : jwtService.getExpirationMs();
        User user = userRepository.findByEmailAddressIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        RefreshToken refreshToken = refreshTokenService.create(user);
        return new AuthResponse("Bearer", accessToken, expiresIn, refreshToken.getToken());
    }

    @Transactional
    public AuthResponse refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenService.validate(refreshTokenValue);
        User user = refreshToken.getUser();
        refreshToken.setRevoked(true);
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmailAddress());
        String newAccessToken = jwtService.generateToken(userDetails, false);
        RefreshToken newRefreshToken = refreshTokenService.create(user);
        return new AuthResponse("Bearer", newAccessToken, jwtService.getExpirationMs(), newRefreshToken.getToken());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenService.revoke(refreshTokenValue);
    }
}
