package com.asa.asaunify.repos;


import com.asa.asaunify.entity.Department;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepo extends JpaRepository<Request, UUID> {

    // ─── Basic lookups ───────────────────────────────────────

    Optional<Request> findByCaseId(String referenceNumber);

    // ─── Initiator queries (My Cases report) ────────────────

    List<Request> findByInitiatorOrderByCreatedAtDesc(User initiator);

    List<Request> findByInitiatorAndStatusOrderByCreatedAtDesc(
            User initiator,
            RequestStatus status
    );

    List<Request> findByInitiatorAndTypeOrderByCreatedAtDesc(
            User initiator,
            RequestType type
    );

    // ─── Department queries (Department Head view) ───────────

    List<Request> findByDepartmentOrderByCreatedAtDesc(Department department);

    List<Request> findByDepartmentAndStatusOrderByCreatedAtDesc(
            Department department,
            RequestStatus status
    );

    List<Request> findByDepartmentAndTypeOrderByCreatedAtDesc(
            Department department,
            RequestType type
    );

    // ─── Type queries (Logistics, Fleet Manager views) ───────

    List<Request> findByTypeAndStatusOrderByCreatedAtDesc(
            RequestType type,
            RequestStatus status
    );

    List<Request> findByTypeOrderByCreatedAtDesc(RequestType type);

    // ─── Admin / Auditor — all requests ─────────────────────

    List<Request> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    // ─── Loan top-ups — find all top-ups for a parent loan ──

    List<Request> findByParentRequestOrderByCreatedAtDesc(Request parentRequest);

    // ─── Date range queries for reports ─────────────────────

    List<Request> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from,
            LocalDateTime to
    );

    List<Request> findByDepartmentAndCreatedAtBetweenOrderByCreatedAtDesc(
            Department department,
            LocalDateTime from,
            LocalDateTime to
    );

    // ─── Overdue requests — due date passed, not completed ──

    @Query("SELECT r FROM Request r WHERE r.dueDate IS NOT NULL " +
            "AND r.dueDate < :now " +
            "AND r.status NOT IN :excludedStatuses")
    List<Request> findOverdueRequests(
            LocalDateTime now,
            @Param("excludedStatuses") List<RequestStatus> excludedStatuses
    );

    // ─── Dashboard KPIs ──────────────────────────────────────

    long countByStatus(RequestStatus status);

    long countByTypeAndStatus(RequestType type, RequestStatus status);

    long countByDepartmentAndStatus(Department department, RequestStatus status);

    // On-time rate — completed before due date
    @Query("SELECT COUNT(r) FROM Request r WHERE r.status = 'COMPLETED' " +
            "AND r.dueDate IS NOT NULL " +
            "AND r.updatedAt <= r.dueDate " +
            "AND r.createdAt BETWEEN :from AND :to")
    long countCompletedOnTime(LocalDateTime from, LocalDateTime to);

    // Total completed in period — denominator for on-time rate
    @Query("SELECT COUNT(r) FROM Request r WHERE r.status = 'COMPLETED' " +
            "AND r.createdAt BETWEEN :from AND :to")
    long countCompletedInPeriod(LocalDateTime from, LocalDateTime to);

    // ─── Reference number generation ─────────────────────────

    // Gets the highest existing reference number to increment
    @Query("SELECT r.caseId FROM Request r " +
            "ORDER BY r.createdAt DESC LIMIT 1")
    Optional<String> findLatestReferenceNumber();
}