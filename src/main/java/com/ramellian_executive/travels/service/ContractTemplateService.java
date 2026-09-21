package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ContractTemplateService {
    private final ContractTemplateRepository repository;
    private final UserRepository userRepository;

    public ContractTemplateService(ContractTemplateRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ContractTemplateResponse submitOwner(ContractTemplateRequest request, Authentication authentication) {
        User owner = findUser(authentication);
        ContractTemplate template = new ContractTemplate();
        template.setOwner(owner);
        template.setName(request.name().trim());
        template.setContent(request.content().trim());
        template.setVersion(nextVersion(owner.getId()));
        template.setStatus(ContractTemplateStatus.PENDING_APPROVAL);
        return ContractTemplateResponse.from(repository.save(template));
    }

    @Transactional
    public ContractTemplateResponse createPlatform(ContractTemplateRequest request) {
        ContractTemplate template = new ContractTemplate();
        template.setName(request.name().trim());
        template.setContent(request.content().trim());
        template.setVersion(nextVersion(null));
        template.setStatus(ContractTemplateStatus.APPROVED);
        return ContractTemplateResponse.from(repository.save(template));
    }

    @Transactional(readOnly = true)
    public List<ContractTemplateResponse> mine(Authentication authentication) {
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(findUser(authentication).getId())
                .stream().map(ContractTemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ContractTemplateResponse> all() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(ContractTemplateResponse::from).toList();
    }

    @Transactional
    public ContractTemplateResponse review(UUID id, ContractTemplateReviewRequest request) {
        ContractTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract template not found"));
        ContractTemplateStatus status;
        try {
            status = ContractTemplateStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid template review status");
        }
        if (template.getOwner() == null || (status != ContractTemplateStatus.APPROVED
                && status != ContractTemplateStatus.REJECTED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only owner templates can be approved or rejected");
        }
        template.setStatus(status);
        return ContractTemplateResponse.from(repository.save(template));
    }

    @Transactional(readOnly = true)
    public String resolveFor(Booking booking) {
        return repository.findFirstByOwnerIdAndStatusOrderByVersionDesc(
                        booking.getVehicle().getOwner().getId(), ContractTemplateStatus.APPROVED)
                .or(() -> repository.findFirstByOwnerIsNullAndStatusOrderByVersionDesc(
                        ContractTemplateStatus.APPROVED))
                .map(ContractTemplate::getContent)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No approved contract template is available"));
    }

    private int nextVersion(UUID ownerId) {
        return repository.findAll().stream()
                .filter(template -> ownerId == null
                        ? template.getOwner() == null
                        : template.getOwner() != null && ownerId.equals(template.getOwner().getId()))
                .mapToInt(ContractTemplate::getVersion).max().orElse(0) + 1;
    }

    private User findUser(Authentication authentication) {
        return userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
