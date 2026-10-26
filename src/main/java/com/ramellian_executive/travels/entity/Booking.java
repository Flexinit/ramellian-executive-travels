package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "booking", indexes = {
    @Index(name = "idx_booking_vehicle_status_time",   columnList = "vehicle_id,status,start_time,end_time"),
    @Index(name = "idx_booking_chauffeur_status_time", columnList = "chauffeur_id,status,start_time,end_time"),
    @Index(name = "idx_booking_user_status",           columnList = "user_id,status,start_time"),
    @Index(name = "idx_booking_status_created",        columnList = "status,created_at"),
    @Index(name = "idx_booking_group",                 columnList = "booking_group_id")
})
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

    @Column(name = "pickup_location", length = 500)
    private String pickupLocation;

    @Column(name = "dropoff_location", length = 500)
    private String dropoffLocation;

    @Column(name = "delivery_requested", nullable = false)
    private boolean deliveryRequested = false;

    @Column(name = "renter_contact_email")
    private String renterContactEmail;

    @Column(name = "renter_contact_phone")
    private String renterContactPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_group_id")
    private BookingGroup bookingGroup;

    @Column(name = "actual_end_time")
    private ZonedDateTime actualEndTime;

    @Column(name = "postponement_count", nullable = false)
    private int postponementCount = 0;

    @Column(name = "passenger_count")
    private Integer passengerCount;

    @Column(name = "pickup_reminder_sent_at")
    private ZonedDateTime pickupReminderSentAt;

    public void setStatus(String status) {
        this.status = BookingStatus.valueOf(status.trim().toUpperCase());
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
