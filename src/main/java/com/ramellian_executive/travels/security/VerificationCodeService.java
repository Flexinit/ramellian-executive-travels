package com.ramellian_executive.travels.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class VerificationCodeService {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    public String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean matches(String code, String hash) {
        return hash != null && MessageDigest.isEqual(
                hash(code).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
    }
}
