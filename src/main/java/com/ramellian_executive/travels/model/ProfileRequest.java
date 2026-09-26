package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @Size(max = 100) String lastName,
        @Size(max = 30) String phoneNumber,
        @Size(max = 255) String location,
        @Size(max = 100) String idNumber,
        @Size(max = 1000) String idNumberUrlFront,
        @Size(max = 1000) String idNumberUrlBack,
        @Size(max = 1000) String profilePhotoUrl,
        @Size(max = 1000) String drivingLicenseFrontUrl,
        @Size(max = 1000) String drivingLicenseBackUrl) {
}