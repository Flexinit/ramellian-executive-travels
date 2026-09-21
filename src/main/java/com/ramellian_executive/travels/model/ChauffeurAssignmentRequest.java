package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChauffeurAssignmentRequest(@NotNull UUID chauffeurId) {
}
