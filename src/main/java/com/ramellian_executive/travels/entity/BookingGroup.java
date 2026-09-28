package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "booking_group")
public class BookingGroup extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_id", nullable = false)
    private User renter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingGroupStatus status = BookingGroupStatus.PENDING;
}
