package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Entity
@Table(name = "b2c_callback_results", indexes = {
    @Index(name = "idx_b2c_callback_disbursement", columnList = "disbursement_id"),
    @Index(name = "idx_b2c_callback_originator",   columnList = "originator_conversation_id"),
    @Index(name = "idx_b2c_callback_transaction",  columnList = "transaction_id")
})
public class B2cCallbackResult extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_id")
    private Disbursement disbursement;

    @Column(name = "result_type")
    private Integer resultType;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc")
    private String resultDesc;

    @Column(name = "originator_conversation_id")
    private String originatorConversationId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "transaction_amount")
    private Double transactionAmount;

    @Column(name = "transaction_receipt")
    private String transactionReceipt;

    @Column(name = "receiver_party_public_name")
    private String receiverPartyPublicName;

    @Column(name = "transaction_completed_at")
    private String transactionCompletedDateTime;

    @Column(name = "utility_account_funds")
    private Double b2cUtilityAccountAvailableFunds;

    @Column(name = "working_account_funds")
    private Double b2cWorkingAccountAvailableFunds;

    @Column(name = "recipient_is_registered_customer")
    private String recipientIsRegisteredCustomer;

    @Column(name = "charges_paid_account_funds")
    private Double b2cChargesPaidAccountAvailableFunds;

    @Column(name = "received_at", nullable = false)
    private ZonedDateTime receivedAt;
}
