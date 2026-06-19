package com.asa.asaunify.entity;


import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_stages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The request this stage belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    // Order of this stage in the chain — starts at 1
    // For parallel stages (loans) all stages share the same index
    @Column(name = "stage_index", nullable = false)
    private Integer stageIndex;

    // The role responsible for acting on this stage
    // e.g. DEPARTMENT_HEAD, LOGISTICS, MSME_OFFICER
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false)
    private Role assignedRole;

    // The specific user who acted on this stage
    // null until someone acts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acted_by")
    private User actedBy;

    // Whether this stage runs in parallel with others
    // true  → loan approval (all approvers act independently)
    // false → equipment/vehicle (one stage at a time)
    @Column(name = "is_parallel", nullable = false)
    @Builder.Default
    private boolean isParallel = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StageStatus status = StageStatus.PENDING;

    // Comment left by the approver when approving or rejecting
    @Column(columnDefinition = "TEXT")
    private String comment;

    // When this stage became active (request reached this stage)
    // Used to compute average task time in reports
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    // When the approver actually acted (approved or rejected)
    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    @PrePersist
    protected void onCreate() {
        this.assignedAt = LocalDateTime.now();
    }

    // How long this stage took to complete in minutes
    // Returns null if not yet acted on
    public Long getDurationMinutes() {
        if (this.assignedAt == null || this.actedAt == null) return null;
        return java.time.Duration.between(this.assignedAt, this.actedAt).toMinutes();
    }

    public boolean isPending() {
        return this.status == StageStatus.PENDING;
    }

    public boolean isApproved() {
        return this.status == StageStatus.APPROVED;
    }

    public boolean isRejected() {
        return this.status == StageStatus.REJECTED;
    }
}