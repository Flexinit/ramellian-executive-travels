package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.BookingPolicy;

import java.util.UUID;

public record BookingPolicyResponse(
        UUID id,
        UUID ownerId,
        int freeCancellationWindowHours,
        int cancellationDeadlineHours,
        int lateCancellationRefundPct,
        boolean postponementAllowed,
        int postponementDeadlineHours,
        int maxPostponements
) {
    public static BookingPolicyResponse from(BookingPolicy p) {
        return new BookingPolicyResponse(
                p.getId(),
                p.getOwner() != null ? p.getOwner().getId() : null,
                p.getFreeCancellationWindowHours(),
                p.getCancellationDeadlineHours(),
                p.getLateCancellationRefundPct(),
                p.isPostponementAllowed(),
                p.getPostponementDeadlineHours(),
                p.getMaxPostponements()
        );
    }

    // Hardcoded defaults returned when no policy has been configured yet
    public static BookingPolicyResponse defaults() {
        return new BookingPolicyResponse(null, null, 24, 2, 50, true, 48, 2);
    }
}
