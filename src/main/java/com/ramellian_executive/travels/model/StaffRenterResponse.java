package com.ramellian_executive.travels.model;

import java.util.UUID;

public record StaffRenterResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String temporaryPassword,
        boolean accountAlreadyExisted
) {}
