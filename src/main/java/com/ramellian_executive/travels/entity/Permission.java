package com.ramellian_executive.travels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "permission")
public class Permission extends AbstractEntity{

    public static final String PROFILE_READ = "PROFILE_READ";
    public static final String BOOKING_CREATE = "BOOKING_CREATE";
    public static final String BOOKING_READ = "BOOKING_READ";
    public static final String BOOKING_UPDATE = "BOOKING_UPDATE";
    public static final String BOOKING_MANAGE = "BOOKING_MANAGE";
    public static final String BOOKING_DELETE = "BOOKING_DELETE";
    public static final String PAYMENT_CREATE = "PAYMENT_CREATE";
    public static final String PAYMENT_READ = "PAYMENT_READ";
    public static final String PAYMENT_MANAGE = "PAYMENT_MANAGE";
    public static final String PAYMENT_DELETE = "PAYMENT_DELETE";
    public static final String VEHICLE_READ = "VEHICLE_READ";
    public static final String VEHICLE_CREATE = "VEHICLE_CREATE";
    public static final String VEHICLE_UPDATE = "VEHICLE_UPDATE";
    public static final String VEHICLE_MANAGE = "VEHICLE_MANAGE";
    public static final String VEHICLE_DELETE = "VEHICLE_DELETE";
    public static final String VEHICLE_REVIEW = "VEHICLE_REVIEW";
    public static final String VEHICLE_APPROVE = "VEHICLE_APPROVE";
    public static final String REVIEW_CREATE = "REVIEW_CREATE";
    public static final String REVIEW_READ = "REVIEW_READ";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String ROLE_READ = "ROLE_READ";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String PERMISSION_READ = "PERMISSION_READ";
    public static final String PERMISSION_MANAGE = "PERMISSION_MANAGE";
    public static final String ANNOUNCEMENT_CREATE = "ANNOUNCEMENT_CREATE";
    public static final String ANNOUNCEMENT_READ = "ANNOUNCEMENT_READ";
    public static final String ANNOUNCEMENT_MANAGE = "ANNOUNCEMENT_MANAGE";
    public static final String ANNOUNCEMENT_DELETE = "ANNOUNCEMENT_DELETE";
    public static final String DOCUMENT_UPLOAD = "DOCUMENT_UPLOAD";
    public static final String DOCUMENT_READ = "DOCUMENT_READ";
    public static final String DOCUMENT_REVIEW = "DOCUMENT_REVIEW";
    public static final String AUDIT_READ = "AUDIT_READ";


    @Column(name = "permission_name", nullable = false, unique = true)
    private String permissionName;

    @Column(name = "description")
    private String description;
}
