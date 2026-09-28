package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.RentalTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RentalTermRepository extends JpaRepository<RentalTerm, UUID> {
    List<RentalTerm> findAllByEnabledTrueOrderByDisplayOrderAsc();
    List<RentalTerm> findByCategoryIgnoreCaseAndEnabledTrue(String category);
    boolean existsByTitleIgnoreCase(String title);
}
