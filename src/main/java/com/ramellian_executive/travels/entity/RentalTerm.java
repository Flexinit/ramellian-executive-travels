package com.ramellian_executive.travels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rental_term")
public class RentalTerm extends AbstractEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "category")
    private String category;

    @Column(name = "display_order", columnDefinition = "int default 0")
    private int displayOrder;
}
