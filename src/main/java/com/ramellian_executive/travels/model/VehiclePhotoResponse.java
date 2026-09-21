package com.ramellian_executive.travels.model;

import java.util.UUID;
public record VehiclePhotoResponse(UUID id, String fileName, String contentType, String url) {}
