package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "booking_contract")
public class BookingContract extends AbstractEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContractStatus status = ContractStatus.PENDING_SIGNATURE;

    @Column(name = "contract_text", nullable = false, columnDefinition = "TEXT")
    private String contractText;

    @Column(name = "signed_at")
    private ZonedDateTime signedAt;

    @Column(name = "signed_by")
    private String signedBy;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "signed_file_name")
    private String signedFileName;

    @Column(name = "document_hash")
    private String documentHash;

    // renter signing audit
    @Column(name = "signer_ip_address")
    private String signerIpAddress;

    @Column(name = "signer_user_agent", length = 1000)
    private String signerUserAgent;

    @Column(name = "renter_signature_file_name")
    private String renterSignatureFileName;

    // owner signing
    @Column(name = "owner_signed_at")
    private ZonedDateTime ownerSignedAt;

    @Column(name = "owner_signed_by")
    private String ownerSignedBy;

    @Column(name = "owner_signature_file_name")
    private String ownerSignatureFileName;

    @Column(name = "owner_signer_ip_address")
    private String ownerSignerIpAddress;

    @Column(name = "owner_signer_user_agent", length = 1000)
    private String ownerSignerUserAgent;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @Column(name = "accepted_at")
    private ZonedDateTime acceptedAt;

    @Column(name = "accepted_by")
    private String acceptedBy;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;
}
