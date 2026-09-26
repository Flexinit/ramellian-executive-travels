
package com.ramellian_executive.travels.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.ZonedDateTime;
import java.util.UUID;


@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
@ToString
public class AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    public UUID id;

    @Column(name = "created_by")
     UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "all_info_given",columnDefinition = "boolean default false")
    private boolean allInfoGiven;

    @JsonIgnore
    @LastModifiedDate
    @Column(name = "last_modified_at")
    private ZonedDateTime lastModifiedAt;

    @JsonIgnore
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @JsonIgnore
    @CreatedDate
    @Column(name = "updated_at",nullable = true, updatable = true)
    private ZonedDateTime updatedAt;
    @PrePersist
    public void onPrePersist() {
        this.createdAt = ZonedDateTime.now();
        this.lastModifiedAt = ZonedDateTime.now();
    }

    @PreUpdate
    public void onPreUpdate() {
        this.lastModifiedAt = ZonedDateTime.now();
    }

}

