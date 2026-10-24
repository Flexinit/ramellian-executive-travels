package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.model.ForgotPasswordRequest;
import com.ramellian_executive.travels.model.ResetPasswordRequest;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.UserRoleRepository;
import com.ramellian_executive.travels.security.JwtService;
import com.ramellian_executive.travels.security.VerificationCodeService;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import com.ramellian_executive.travels.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {

        return authService.register(request);
    }


    @PostMapping("/verify-email")
    public void verifyEmail(@Valid @RequestBody VerifyCodeRequest request) {
        authService.verifyEmail(request);
    }

    @PostMapping("/verify-code")
    public void verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        authService.verifyEmail(request);
    }

    @PostMapping("/verify-whatsapp")
    public void verifyWhatsApp(@Valid @RequestBody VerifyCodeRequest request) {
        authService.verifyEmail(request);
    }

    @PostMapping("/send-whatsapp-code")
    @Transactional
    public void sendWhatsAppCode(@RequestBody WhatsAppVerificationRequest request) {
        authService.sendWhatsAppVerification(request);
    }

    @PostMapping("/resend-verification")
    @Transactional
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
    }


    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
       return authService.login(request);
    }


    @PostMapping("/verify-2fa")
    @Transactional
    public AuthResponse verifyTwoFactor(@Valid @RequestBody VerifyCodeRequest request) {
       return  authService.verifyTwoFactor(request);
    }

    @PostMapping("/google")
    public AuthResponse googleSignIn(@Valid @RequestBody GoogleAuthRequest request) {
        return authService.googleSignIn(request);
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-firebase-phone")
    public void verifyFirebasePhone(@Valid @RequestBody FirebasePhoneVerifyRequest request,
                                    Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        authService.verifyFirebasePhone(request, email);
    }
















}
