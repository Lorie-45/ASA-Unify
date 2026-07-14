package com.asa.asaunify.dtos;


import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class MemoDto {

    private UUID id;
    private String referenceNumber;
    private String title;
    private String content;

    // Author info
    private UUID authorId;
    private String authorName;

    // Overall memo status
    private RequestStatus status;

    // All approval stages — all parallel
    private List<MemoApprovalStageDTO> approvalStages;

    // Rejection info — null if not rejected
    private String rejectionReason;
    private String rejectedByName;
    private LocalDateTime rejectedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─── Nested DTO ──────────────────────────────────────────

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class MemoApprovalStageDTO {

        private UUID id;

        // The role assigned to this stage
        private Role assignedRole;

        private UUID assignedToId;       // ← add
        private String assignedToName;

        // The specific user who acted — null until acted
        private String actedByName;

        private StageStatus status;

        // Mandatory on rejection, optional on approval
        private String comment;

        private LocalDateTime assignedAt;
        private LocalDateTime actedAt;
    }
}