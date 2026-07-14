package com.asa.asaunify.entity;


import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "memo_approval_stages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoApprovalStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The memo this stage belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memo_id", nullable = false)
    private Memo memo;

    // The role the author selected to approve this memo
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false)
    private Role assignedRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    // The specific user who acted
    // null until someone with that role acts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acted_by")
    private User actedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StageStatus status = StageStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String comment;

    // When this stage was created (memo submitted)
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    // When the approver acted
    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    @PrePersist
    protected void onCreate() {
        this.assignedAt = LocalDateTime.now();
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