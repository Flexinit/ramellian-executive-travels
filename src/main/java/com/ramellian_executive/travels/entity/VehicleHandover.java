package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "vehicle_handover",
        uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
public class VehicleHandover extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private HandoverType type;

    @Column(name = "handover_time", nullable = false)
    private ZonedDateTime handoverTime;

    @Column(name = "odometer_reading")
    private Integer odometerReading;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_level")
    private FuelLevel fuelLevel;

    @Column(name = "condition_notes", columnDefinition = "TEXT")
    private String conditionNotes;

    @Column(name = "damage_noted")
    private boolean damageNoted;

    @Column(name = "damage_description", columnDefinition = "TEXT")
    private String damageDescription;

    @Column(name = "location")
    private String location;

    @Column(name = "signature_file_name")
    private String signatureFileName;

    @Column(name = "signed_by")
    private String signedBy;

    @Column(name = "signed_at")
    private ZonedDateTime signedAt;

    @Column(name = "signer_ip_address")
    private String signerIpAddress;

    @Column(name = "signer_user_agent")
    private String signerUserAgent;
}
