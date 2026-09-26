package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getUserDetails(Authentication authentication) {
        return userService.getUserDetails(authentication);
    };

    @PatchMapping("/me")
    public UserResponse updateProfile(Authentication authentication, @Valid @RequestBody ProfileRequest request) {
        return userService.updateProfile(authentication, request);
    }

    @PutMapping("/me/password")
    public void changePassword(Authentication authentication, @Valid @RequestBody PasswordRequest request) {
        userService.changePassword(authentication, request);
    }

    @PutMapping("/me/2fa")
    public UserResponse configureTwoFactor(
            Authentication authentication, @Valid @RequestBody TwoFactorRequest request) {

        return userService.configureTwoFactor(authentication, request);
    }

    @PutMapping("/me/owner-profile")
    public UserResponse updateOwnerProfile(Authentication authentication,
                                           @Valid @RequestBody OwnerProfileRequest request) {
        return userService.updateOwnerProfile(authentication, request);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<UserResponse> listUsers() {
         return  userService.listUsers();
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse getUser(@PathVariable UUID id) {
        return  userService.getUser(id);
    }

    @PutMapping("/admin/{id}/enabled")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse setEnabled(@PathVariable UUID id, @RequestBody EnabledRequest request) {
        return userService.setEnabled(id, request);
    }

    @PutMapping("/admin/{id}/role")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public UserResponse setRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return userService.setRole(id, request);
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public void deleteUser(@PathVariable UUID id, Authentication authentication) {
     userService.deleteUser(id,  authentication);

    }
















}
