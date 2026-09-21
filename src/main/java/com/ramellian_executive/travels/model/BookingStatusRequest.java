package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record BookingStatusRequest(@NotBlank String status, String notes) {
}
