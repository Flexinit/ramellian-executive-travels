package com.ramellian_executive.travels.model;

public record AuthResponse(String tokenType, String accessToken, long expiresIn) {
    }