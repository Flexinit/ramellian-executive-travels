package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotNull;

public record BotAssistantToggleRequest(@NotNull Boolean enabled) {}
