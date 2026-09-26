package com.ramellian_executive.travels.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import com.ramellian_executive.travels.security.GoogleIdTokenVerifierService;
import com.ramellian_executive.travels.security.JwtService;
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
                    .body(new AuthResponse(null, "WhatsApp verification code sent", 0));
        } else {
            notificationService.sendEmail(email, code, "email verification");
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(null, "Email verification required", 0));
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
            return new AuthResponse(null, "Two-factor verification required", 0);
        }
        if (auditLogService != null) {
            auditLogService.logAction(email, "AUTH_LOGIN", "USER", user.getId() != null ? user.getId().toString() : null, "User logged in successfully", "SUCCESS");
        }
        return tokenResponse(userDetailsService.loadUserByUsername(email));
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

        return tokenResponse(userDetailsService.loadUserByUsername(user.getEmailAddress()));
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
        return new AuthResponse("Bearer", jwtService.generateToken(userDetails), jwtService.getExpirationMs());
    }
}
