package com.asa.asaunify.repos;


import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalStageRepo extends JpaRepository<ApprovalStage, UUID> {

    // All stages for a request ordered by index
    List<ApprovalStage> findByRequestOrderByStageIndexAsc(Request request);

    // All stages for a request at a specific index
    // Returns multiple rows for parallel stages (loans)
    List<ApprovalStage> findByRequestAndStageIndex(Request request, Integer stageIndex);

    // Current pending stages for a request
    List<ApprovalStage> findByRequestAndStatus(Request request, StageStatus status);

    // Find the current active stage index for a request
    // The lowest index that still has a PENDING stage
    @Query("SELECT MIN(a.stageIndex) FROM ApprovalStage a " +
            "WHERE a.request = :request AND a.status = 'PENDING'")
    Optional<Integer> findCurrentStageIndex(Request request);

    // All pending stages assigned to a specific role
    // Used to build the "Pending Approvals" view per role
    List<ApprovalStage> findByAssignedRoleAndStatus(Role assignedRole, StageStatus status);

    // Check if all stages for a request are approved
    // Returns true only when zero PENDING or REJECTED stages remain
    @Query("SELECT COUNT(a) = 0 FROM ApprovalStage a " +
            "WHERE a.request = :request " +
            "AND a.status != 'APPROVED'")
    boolean areAllStagesApproved(Request request);

    // Check if any stage for a request is rejected
    @Query("SELECT COUNT(a) > 0 FROM ApprovalStage a " +
            "WHERE a.request = :request AND a.status = 'REJECTED'")
    boolean hasAnyRejectedStage(Request request);

    // All stages acted on by a specific user — used in user activity report
    List<ApprovalStage> findByActedBy(User actedBy);


    // Fetch all completed stages for a role
// Average duration is computed in the service layer
    @Query("SELECT a FROM ApprovalStage a WHERE a.assignedRole = :role " +
            "AND a.actedAt IS NOT NULL AND a.status != :excludedStatus")
    List<ApprovalStage> findCompletedStagesByRole(
            @Param("role") Role role,
            @Param("excludedStatus") StageStatus excludedStatus
    );
}