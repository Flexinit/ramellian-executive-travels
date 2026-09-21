package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.ContractTemplate;

import java.util.UUID;

public record ContractTemplateResponse(UUID id, UUID ownerId, String name, int version,
                                       String status, String content) {
    public static ContractTemplateResponse from(ContractTemplate template) {
        return new ContractTemplateResponse(template.getId(),
                template.getOwner() == null ? null : template.getOwner().getId(),
                template.getName(), template.getVersion(),
                template.getStatus().name(), template.getContent());
    }
}
