package com.asa.asaunify.repos;

import com.asa.asaunify.entity.Memo;
import com.asa.asaunify.entity.MemoApprovalStage;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemoApprovalStageRepo extends JpaRepository<MemoApprovalStage, UUID> {

    // All stages for a specific memo
    List<MemoApprovalStage> findByMemoOrderByAssignedAtAsc(Memo memo);

    // All pending stages for a specific memo
    List<MemoApprovalStage> findByMemoAndStatus(Memo memo, StageStatus status);

    // Find a specific stage by memo and role
    // Used when an approver acts on a memo
    Optional<MemoApprovalStage> findByMemoAndAssignedRole(Memo memo, Role assignedRole);

    // All pending stages assigned to a specific role
    // Powers the "Pending Approvals" view for memo approvers
    List<MemoApprovalStage> findByAssignedRoleAndStatus(Role assignedRole, StageStatus status);

    // All stages acted on by a specific user
    List<MemoApprovalStage> findByActedBy(User actedBy);

    // Check if all stages for a memo are approved
    @Query("SELECT COUNT(s) = 0 FROM MemoApprovalStage s " +
            "WHERE s.memo = :memo AND s.status != 'APPROVED'")
    boolean areAllStagesApproved(Memo memo);

    // Check if any stage for a memo is rejected
    @Query("SELECT COUNT(s) > 0 FROM MemoApprovalStage s " +
            "WHERE s.memo = :memo AND s.status = 'REJECTED'")
    boolean hasAnyRejectedStage(Memo memo);

    // Find stage assigned to a specific user
    Optional<MemoApprovalStage> findByMemoAndAssignedToAndStatus(
            Memo memo, User assignedTo, StageStatus status);

    // Find stage assigned to a role with specific status
    Optional<MemoApprovalStage> findByMemoAndAssignedRoleAndStatus(
            Memo memo, Role assignedRole, StageStatus status);
}