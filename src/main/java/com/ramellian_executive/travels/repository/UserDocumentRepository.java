package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.UserDocument;
import com.ramellian_executive.travels.entity.DocumentReviewStatus;
import com.ramellian_executive.travels.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {
    List<UserDocument> findAllByUserOrderByCreatedAtDesc(User user);
    List<UserDocument> findAllByReviewStatusOrderByCreatedAtAsc(DocumentReviewStatus reviewStatus);
    boolean existsByUserIdAndDocumentTypeAndReviewStatus(UUID userId, DocumentType documentType,
                                                         DocumentReviewStatus reviewStatus);
}
