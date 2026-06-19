package com.asa.asaunify.dtos;



import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class RequestResponseDto {

    private UUID id;
    private String caseId;
    private RequestType type;
    private RequestStatus status;
    private String title;
    private String details;
    private String notes;

    // Initiator info
    private UUID initiatorId;
    private String initiatorName;

    // Department info
    private UUID departmentId;
    private String departmentName;

    // Type-specific fields
    private Map<String, Object> extraFields;

    // Approval chain — ordered list of stages
    private List<ApprovalStageDto> approvalStages;

    // Attachments
    private List<AttachmentDto> attachments;

    // Rejection info — null if not rejected
    private String rejectionReason;
    private String rejectedByName;
    private LocalDateTime rejectedAt;

    // Parent loan reference — null if not a top-up
    private String parentReferenceNumber;

    // Timing
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isOverdue;

    // ─── Nested DTOs ─────────────────────────────────────────

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class ApprovalStageDto {
        private UUID id;
        private Integer stageIndex;
        private String assignedRole;
        private String actedByName;
        private String status;
        private String comment;
        private boolean isParallel;
        private LocalDateTime assignedAt;
        private LocalDateTime actedAt;
        private Long durationMinutes;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class AttachmentDto {
        private UUID id;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private LocalDateTime uploadedAt;
        private String uploadedByName;
    }
}