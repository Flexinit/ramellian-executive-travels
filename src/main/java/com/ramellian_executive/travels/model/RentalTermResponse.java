package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.RentalTerm;

import java.time.ZonedDateTime;
import java.util.UUID;

public record RentalTermResponse(
        UUID id,
        String title,
        String content,
        String category,
        int displayOrder,
        boolean enabled,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static RentalTermResponse from(RentalTerm term) {
        return new RentalTermResponse(
                term.getId(),
                term.getTitle(),
                term.getContent(),
                term.getCategory(),
                term.getDisplayOrder(),
                term.isEnabled(),
                term.getCreatedAt(),
                term.getUpdatedAt()
        );
    }
}
