package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "booking")
public class Booking extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "chauffeur_id")
    private User chauffeur;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private ZonedDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_mode", nullable = false)
    private BookingMode bookingMode;

    @Column(name = "dispatcher_notes")
    private String dispatcherNotes;

    @Column(name = "contract_amendments")
    private String contractAmendments;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type")
    private TripType tripType;

    @Column(name = "renter_notes", length = 1000)
    private String renterNotes;

    public void setStatus(String status) {
        this.status = BookingStatus.valueOf(status.trim().toUpperCase());
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
