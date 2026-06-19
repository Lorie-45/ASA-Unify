package com.asa.asaunify.repos;


import com.asa.asaunify.entity.Memo;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemoRepo extends JpaRepository<Memo, UUID> {

    Optional<Memo> findByReferenceNumber(String referenceNumber);

    // All memos written by a specific author
    List<Memo> findByAuthorOrderByCreatedAtDesc(User author);

    // All memos by status
    List<Memo> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    // All memos by a specific author and status
    List<Memo> findByAuthorAndStatusOrderByCreatedAtDesc(User author, RequestStatus status);

    // All memos in a date range — used in reports
    List<Memo> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from,
            LocalDateTime to
    );

    // Latest reference number for auto-increment generation
    @Query("SELECT m.referenceNumber FROM Memo m ORDER BY m.createdAt DESC LIMIT 1")
    Optional<String> findLatestReferenceNumber();

    // Count memos by status — used in dashboard
    long countByStatus(RequestStatus status);

    // All memos that are pending and have a specific role
    // in their approval stages — used to notify approvers
    @Query("SELECT DISTINCT m FROM Memo m JOIN m.approvalStages s " +
            "WHERE s.assignedRole = :role AND s.status = 'PENDING'")
    List<Memo> findPendingMemosByRole(
            @Param("role") com.asa.asaunify.enums.Role role
    );
}