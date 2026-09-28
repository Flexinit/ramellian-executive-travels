package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.RentalTerm;
import com.ramellian_executive.travels.model.RentalTermRequest;
import com.ramellian_executive.travels.model.RentalTermResponse;
import com.ramellian_executive.travels.repository.RentalTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RentalTermService {

    private final RentalTermRepository rentalTermRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<RentalTermResponse> listAll() {
        return rentalTermRepository.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(RentalTerm::getDisplayOrder))
                .map(RentalTermResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RentalTermResponse> listActive() {
        return rentalTermRepository.findAllByEnabledTrueOrderByDisplayOrderAsc().stream()
                .map(RentalTermResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RentalTermResponse> listByCategory(String category) {
        return rentalTermRepository.findByCategoryIgnoreCaseAndEnabledTrue(category).stream()
                .map(RentalTermResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RentalTermResponse getById(UUID id) {
        return RentalTermResponse.from(findOrThrow(id));
    }

    @Transactional
    public RentalTermResponse create(RentalTermRequest request, Authentication authentication) {
        if (rentalTermRepository.existsByTitleIgnoreCase(request.title().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A rental term with title '" + request.title() + "' already exists");
        }

        RentalTerm term = new RentalTerm();
        term.setTitle(request.title().trim());
        term.setContent(request.content().trim());
        term.setCategory(request.category() != null ? request.category().trim().toUpperCase() : null);
        term.setDisplayOrder(request.displayOrder());
        term.setEnabled(true);

        RentalTerm saved = rentalTermRepository.save(term);
        auditLogService.logAction(authentication, "RENTAL_TERM_CREATE", "RENTAL_TERM",
                saved.getId().toString(), "Created rental term: " + saved.getTitle(), "SUCCESS");
        return RentalTermResponse.from(saved);
    }

    @Transactional
    public RentalTermResponse update(UUID id, RentalTermRequest request, Authentication authentication) {
        RentalTerm term = findOrThrow(id);

        String newTitle = request.title().trim();
        if (!term.getTitle().equalsIgnoreCase(newTitle)
                && rentalTermRepository.existsByTitleIgnoreCase(newTitle)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A rental term with title '" + newTitle + "' already exists");
        }

        term.setTitle(newTitle);
        term.setContent(request.content().trim());
        term.setCategory(request.category() != null ? request.category().trim().toUpperCase() : null);
        term.setDisplayOrder(request.displayOrder());

        RentalTerm saved = rentalTermRepository.save(term);
        auditLogService.logAction(authentication, "RENTAL_TERM_UPDATE", "RENTAL_TERM",
                saved.getId().toString(), "Updated rental term: " + saved.getTitle(), "SUCCESS");
        return RentalTermResponse.from(saved);
    }

    @Transactional
    public RentalTermResponse toggleEnabled(UUID id, Authentication authentication) {
        RentalTerm term = findOrThrow(id);
        term.setEnabled(!term.isEnabled());
        RentalTerm saved = rentalTermRepository.save(term);
        String action = saved.isEnabled() ? "RENTAL_TERM_ENABLE" : "RENTAL_TERM_DISABLE";
        auditLogService.logAction(authentication, action, "RENTAL_TERM",
                saved.getId().toString(), "Toggled rental term: " + saved.getTitle(), "SUCCESS");
        return RentalTermResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, Authentication authentication) {
        RentalTerm term = findOrThrow(id);
        rentalTermRepository.delete(term);
        auditLogService.logAction(authentication, "RENTAL_TERM_DELETE", "RENTAL_TERM",
                id.toString(), "Deleted rental term: " + term.getTitle(), "SUCCESS");
    }

    private RentalTerm findOrThrow(UUID id) {
        return rentalTermRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rental term not found with id: " + id));
    }
}
