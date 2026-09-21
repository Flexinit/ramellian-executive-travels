package com.ramellian_executive.travels.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerificationCodeServiceTest {

    private final VerificationCodeService service = new VerificationCodeService();

    @Test
    void generatedCodesAreSixDigitsAndOnlyTheirHashMatches() {
        String code = service.generateCode();
        String hash = service.hash(code);

        assertTrue(code.matches("\\d{6}"));
        assertTrue(service.matches(code, hash));
        assertFalse(service.matches("000000".equals(code) ? "000001" : "000000", hash));
    }

    @Test
    void missingHashNeverMatches() {
        assertFalse(service.matches("123456", null));
    }
}
