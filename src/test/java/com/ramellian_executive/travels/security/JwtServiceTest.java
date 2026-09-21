package com.ramellian_executive.travels.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void generatesTokenClaimsIncludingIdentityRolesAndPermissions() {
        JwtService jwtService = new JwtService("this-is-a-very-secure-jwt-secret-key-for-tests-12345", 3600000L);
        UUID userId = UUID.randomUUID();
        JwtUserPrincipal principal = new JwtUserPrincipal(
                userId,
                "person@example.com",
                "encoded-password",
                Set.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("VEHICLE_MANAGE")));

        String token = jwtService.generateToken(principal);

        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals("person@example.com", jwtService.extractEmail(token));
        assertEquals("person@example.com", jwtService.extractUsername(token));

        Claims claims = Jwts.parser().verifyWith(jwtService.getSigningKey()).build()
                .parseSignedClaims(token)
                .getPayload();

        assertTrue(claims.get("roles", List.class).contains("ADMIN"));
        assertTrue(claims.get("permissions", List.class).contains("VEHICLE_MANAGE"));
        assertEquals(userId.toString(), claims.get("userId"));
        assertEquals("person@example.com", claims.get("email"));
    }
}
