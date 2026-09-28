package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "booking_lead")
public class BookingLead extends AbstractEntity {

    @Column(name = "email")
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private ZonedDateTime endTime;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "lead_created_at", nullable = false, updatable = false)
    private ZonedDateTime leadCreatedAt;

    @PrePersist
    @Override
    public void onPrePersist() {
        super.onPrePersist();
        this.leadCreatedAt = ZonedDateTime.now();
    }
}
