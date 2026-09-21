package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
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
        user.setEnabled(true);
        user.setEmailVerified(false);
        UserRole renterRole = userRoleRepository.findByRoleName(UserRole.RENTER)
                .orElseGet(() -> {
                    UserRole role = new UserRole();
                    role.setRoleName(UserRole.RENTER);
                    role.setDescription("User who rents vehicles");
                    return userRoleRepository.save(role);
                });
        user.getRoles().add(renterRole);
        String code = verificationCodeService.generateCode();
        user.setVerificationCodeHash(verificationCodeService.hash(code));
        user.setVerificationCodeExpiresAt(ZonedDateTime.now().plusMinutes(codeExpirationMinutes));
        userRepository.save(user);
        notificationService.sendEmail(email, code, "email verification");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(null, "Email verification required", 0));


    }

    @Transactional
    public void verifyEmail(VerifyCodeRequest request){
        User user = findUser(request.email());
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
    }


    @Transactional
    public void resendVerification( ResendVerificationRequest request) {
        User user = findUser(request.email());
        if (user.isEmailVerified()) {
            return;
        }
        String code = verificationCodeService.generateCode();
        user.setVerificationCodeHash(verificationCodeService.hash(code));
        user.setVerificationCodeExpiresAt(ZonedDateTime.now().plusMinutes(codeExpirationMinutes));
        userRepository.save(user);
        notificationService.sendEmail(user.getEmailAddress(), code, "email verification");
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
            return new AuthResponse(null, "Two-factor verification required", 0);
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
        return tokenResponse(userDetailsService.loadUserByUsername(user.getEmailAddress()));
    }

    private User findUser(String email) {
        return userRepository.findByEmailAddressIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
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
