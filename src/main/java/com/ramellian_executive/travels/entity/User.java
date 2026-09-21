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

    @Column(name = "id_number")
    private String idNumber;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "profile_photo")
    private String profilePhoto;

    @Column(name = "password")
    private String password;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "location")
    private String location;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

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

}