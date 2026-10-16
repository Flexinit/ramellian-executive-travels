package com.ramellian_executive.travels.entity;

public enum ContractStatus {
    PENDING_SIGNATURE,
    SIGNED,           // renter has signed; awaiting owner
    FULLY_SIGNED,     // both renter and owner have signed
    VOID
}
