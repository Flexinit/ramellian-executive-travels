package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.SplitPaymentContribution;
import com.ramellian_executive.travels.entity.SplitPaymentGroup;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record SplitPaymentResponse(
        UUID splitGroupId,
        UUID bookingId,
        String groupStatus,
        double totalAmount,
        double totalPaid,
        double totalOutstanding,
        ZonedDateTime expiresAt,
        List<ContributionResponse> contributions,
        UUID masterPaymentId   // set once all contributions are paid
) {
    public record ContributionResponse(
            UUID   contributionId,
            String payerName,
            String payerPhone,
            String payerEmail,
            double amount,
            String status,
            String paymentMethod,
            String receipt,
            ZonedDateTime updatedAt
    ) {}

    public static SplitPaymentResponse from(SplitPaymentGroup group,
                                            List<SplitPaymentContribution> contributions) {
        double totalPaid = contributions.stream()
                .filter(c -> c.getStatus() != null
                        && c.getStatus().name().equals("PAID"))
                .mapToDouble(SplitPaymentContribution::getAmount).sum();

        List<ContributionResponse> contribResponses = contributions.stream()
                .map(c -> new ContributionResponse(
                        c.getId(),
                        c.getPayerName(),
                        c.getPayerPhone(),
                        c.getPayerEmail(),
                        c.getAmount(),
                        c.getStatus() != null ? c.getStatus().name() : null,
                        c.getPaymentMethod(),
                        c.getProviderReceipt(),
                        c.getUpdatedAt()
                ))
                .toList();

        return new SplitPaymentResponse(
                group.getId(),
                group.getBooking().getId(),
                group.getStatus() != null ? group.getStatus().name() : null,
                group.getTotalAmount(),
                totalPaid,
                group.getTotalAmount() - totalPaid,
                group.getExpiresAt(),
                contribResponses,
                group.getPayment() != null ? group.getPayment().getId() : null
        );
    }
}
