package com.ramellian_executive.travels.model;

import java.time.ZonedDateTime;

public record SavedSignatureResponse(boolean hasSavedSignature, ZonedDateTime consentGivenAt) {}
