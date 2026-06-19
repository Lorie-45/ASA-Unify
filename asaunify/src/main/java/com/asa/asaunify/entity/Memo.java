package com.asa.asaunify.entity;


import com.asa.asaunify.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "memos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Memo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Human-readable reference e.g. MEMO-001
    @Column(name = "reference_number", nullable = false, unique = true)
    private String referenceNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Who wrote the memo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // Reusing RequestStatus for memo lifecycle
    // DRAFT → PENDING → APPROVED / REJECTED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RequestStatus status = RequestStatus.DRAFT;

    // All approval stages — one per role the author selected
    // All run in parallel
    @OneToMany(mappedBy = "memo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MemoApprovalStage> approvalStages = new ArrayList<>();

    // Rejection tracking — if any approver rejects
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}