package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking_policy")
@Getter
@Setter
@NoArgsConstructor
public class BookingPolicy extends AbstractEntity {

    // null = platform-wide default; non-null = owner-specific override
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Renter may cancel free of charge if >= this many hours remain before start
    @Column(name = "free_cancellation_window_hours", nullable = false)
    private int freeCancellationWindowHours = 24;

    // Renter cannot cancel within this many hours of start (hard deadline)
    @Column(name = "cancellation_deadline_hours", nullable = false)
    private int cancellationDeadlineHours = 2;

    // Refund % when cancelling after the free window but before the hard deadline
    @Column(name = "late_cancellation_refund_pct", nullable = false)
    private int lateCancellationRefundPct = 50;

    @Column(name = "postponement_allowed", nullable = false)
    private boolean postponementAllowed = true;

    // Renter must postpone at least this many hours before the booking start
    @Column(name = "postponement_deadline_hours", nullable = false)
    private int postponementDeadlineHours = 48;

    // Maximum number of postponements per booking
    @Column(name = "max_postponements", nullable = false)
    private int maxPostponements = 2;
}
