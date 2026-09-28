package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record RentalTermRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Content is required") String content,
        String category,
        int displayOrder
) {}
