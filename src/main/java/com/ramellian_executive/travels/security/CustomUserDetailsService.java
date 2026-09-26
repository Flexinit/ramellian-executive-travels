package com.ramellian_executive.travels.security;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserRole;
import com.ramellian_executive.travels.entity.Permission;
import com.ramellian_executive.travels.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAddressIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Set<String> roles = user.getRoles().stream()
                .map(UserRole::getRoleName)
                .collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getPermissionName)
                .collect(Collectors.toSet());
        if (user.getDirectPermissions() != null) {
            permissions.addAll(user.getDirectPermissions().stream()
                    .map(Permission::getPermissionName)
                    .collect(Collectors.toSet()));
        }
        if (roles.isEmpty()) {
            roles.add(UserRole.CAR_RENTER);
        }

        Set<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
        authorities.addAll(permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet()));

        return new JwtUserPrincipal(
                user.getId(),
                user.getEmailAddress(),
                user.getPassword(),
                authorities);
    }
}
