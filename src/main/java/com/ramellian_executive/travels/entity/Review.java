package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "review")
public class Review extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "rating", nullable = false)
    private Double rating;

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "complaint")
    private String complaint;

}
