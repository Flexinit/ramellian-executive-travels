package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "vehicle")
public class Vehicle extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "model")
    private String model;

    @Column(name = "VIN")
    private String VIN;

    @Column(name = "registrationNumber")
    private String registrationNumber;

    @Column(name = "location")
    private String location;


    @Column(name = "price_per_day")
    private Double pricePerDay;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "available", columnDefinition = "boolean default false")
    private boolean available;


    @Column(name = "available_from")
    private ZonedDateTime availableFrom;

    @Column(name = "available_to")
    private ZonedDateTime availableTo;

    @Column(name = "has_working_ac", columnDefinition = "boolean default false")
    private boolean hasWorkingAC;

    @Column(name = "comprehensive_insurance", columnDefinition = "boolean default false")
    private boolean comprehensiveInsurance;

    @Column(name = "insurance_verification_sticker")
    private String insuranceVerificationSticker;

    @Column(name = "capacity")
    private int capacity;

    @Column(name = "color")
    private String color;

    @Column(name = "fuel_type")
    private String fuelType;

    @Column(name = "transmission_type")
    private String transmissionType;

    @Column(name = "self_drive", columnDefinition = "boolean default false")
    private boolean selfDrive;

    @Column(name = "chauffeured", columnDefinition = "boolean default false")
    private boolean chauffeured;

    @ElementCollection(targetClass = TripType.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "vehicle_trip_types", joinColumns = @JoinColumn(name = "vehicle_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type")
    private Set<TripType> tripTypes = new HashSet<>();
}
