package com.ramellian_executive.travels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pesapal_event")
public class PesapalEvent extends AbstractEntity {
    @Column(name = "order_tracking_id", nullable = false)
    private String orderTrackingId;

    @Column(name = "merchant_reference", nullable = false)
    private String merchantReference;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "raw_status")
    private String rawStatus;
}
