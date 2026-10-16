package com.ramellian_executive.travels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "app_users")
public class User extends AbstractEntity {

    @Column(name = "owner_type")
    private String ownerType;

    @Column(name = "is_car_owner", nullable = false, columnDefinition = "boolean default false")
    private boolean isCarOwner;

    @Column(name = "is_car_renter", nullable = false, columnDefinition = "boolean default false")
    private boolean isCarRenter;

    @Column(name = "has_added_vehicle_to_profile", nullable = false, columnDefinition = "boolean default false")
    private boolean addedVehicleToProfile;

    @Column(name = "has_completed_profile", nullable = false, columnDefinition = "boolean default false")
    private boolean completedProfile;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "business_registration_number")
    private String businessRegistrationNumber;

    @Column(name = "business_tax_number")
    private String businessTaxNumber;

    @Column(name = "business_address")
    private String businessAddress;

    @Column(name = "payout_provider")
    private String payoutProvider;

    @Column(name = "payout_account_reference")
    private String payoutAccountReference;

    @Column(name = "payout_status")
    private String payoutStatus = "NOT_CONFIGURED";

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email_address", nullable = false, unique = true)
    private String emailAddress;

    @Column(name = "id_number", unique = true)
    private String idNumber;

    @Column(name = "id_number_url_front")
    private String idNumberUrlFront;

    @Column(name = "id_number_url_back")
    private String idNumberUrlBack;

    @Column(name = "driving_license_front_url")
    private String drivingLicenseFrontUrl;

    @Column(name = "driving_license_back_url")
    private String drivingLicenseBackUrl;

    @Column(name = "driving_licence_number")
    private String drivingLicenceNumber;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "password")
    private String password;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "auth_provider")
    private String authProvider = "EMAIL";

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "location")
    private String location;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean phoneVerified;

    @Column(name = "signature_file_name")
    private String signatureFileName;

    @Column(name = "signature_consent_given_at")
    private java.time.ZonedDateTime signatureConsentGivenAt;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "two_factor_method")
    private String twoFactorMethod;

    @Column(name = "verification_code_hash")
    private String verificationCodeHash;

    @Column(name = "verification_code_expires_at")
    private ZonedDateTime verificationCodeExpiresAt;

    @Column(name = "two_factor_code_hash")
    private String twoFactorCodeHash;

    @Column(name = "two_factor_code_expires_at")
    private ZonedDateTime twoFactorCodeExpiresAt;

    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<UserRole> roles = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "user_direct_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> directPermissions = new HashSet<>();
}