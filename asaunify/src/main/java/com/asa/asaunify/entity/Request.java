package com.asa.asaunify.entity;


import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Human-readable reference number e.g. CN-001, CN-002
    // Generated in RequestService before save
    @Column(name = "case_id", nullable = false, unique = true)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RequestStatus status = RequestStatus.DRAFT;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Who created this request
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", nullable = false)
    private User initiator;

    // Which department this request belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // Optional due date — used for on-time rate KPI in reports
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    // Type-specific fields stored as JSONB
    // Equipment → { "item_name": "Laptop", "quantity": 2 }
    // Vehicle   → { "destination": "Musanze", "trip_date": "2026-06-15", "purpose": "Field visit" }
    // Loan      → { "amount": 3000000, "loan_type": "MSME_SILVER", "is_top_up": false }
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_fields", columnDefinition = "jsonb")
    private Map<String, Object> extraFields;

    // For loan top-ups — links back to the original loan request
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_request_id")
    private Request parentRequest;

    // Rejection / cancellation — covers both workflow rejections
    // and admin/authorized cancellations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // All approval stages — built by WorkflowEngine at submission time
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ApprovalStage> approvalStages = new ArrayList<>();

    // All file attachments uploaded with this request
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RequestAttachment> attachments = new ArrayList<>();

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

    // Used in reports for overdue detection
    public boolean isOverdue() {
        return this.dueDate != null
                && LocalDateTime.now().isAfter(this.dueDate)
                && this.status != RequestStatus.COMPLETED
                && this.status != RequestStatus.REJECTED;
    }

    // Convenience getter for loan-specific extra fields
    public Object getExtraField(String key) {
        return this.extraFields != null ? this.extraFields.get(key) : null;
    }
}