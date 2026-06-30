package com.asa.asaunify.services;




import com.asa.asaunify.dtos.CreateMemoRequest;
import com.asa.asaunify.dtos.MemoApprovalActionDto;
import com.asa.asaunify.dtos.MemoDto;
import com.asa.asaunify.entity.Memo;
import com.asa.asaunify.entity.MemoApprovalStage;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.MemoApprovalStageRepo;
import com.asa.asaunify.repos.MemoRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoService {

    private final MemoRepo memoRepository;
    private final MemoApprovalStageRepo memoApprovalStageRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ─── Create & Submit ──────────────────────────────────────

    @Transactional
    public MemoDto createMemo(
            CreateMemoRequest dto,
            User author,
            HttpServletRequest httpRequest) {

        Memo memo = Memo.builder()
                .referenceNumber(generateReferenceNumber())
                .title(dto.getTitle())
                .content(dto.getContent())
                .author(author)
                .status(RequestStatus.PENDING)
                .build();

        Memo saved = memoRepository.save(memo);

        // Create one parallel approval stage per selected role
        for (Role role : dto.getApproverRoles()) {
            MemoApprovalStage stage = MemoApprovalStage.builder()
                    .memo(saved)
                    .assignedRole(role)
                    .status(StageStatus.PENDING)
                    .build();
            memoApprovalStageRepository.save(stage);
        }

        // Notify all selected approver roles immediately
        notificationService.notifyMemoSubmitted(saved);

        auditService.log(
                author,
                ActionType.MEMO_CREATED,
                "MEMO",
                saved.getId().toString(),
                "MEMOS",
                httpRequest
        );

        return toDTO(memoRepository.findById(saved.getId()).orElseThrow());
    }

    // ─── Approve / Reject ─────────────────────────────────────

    @Transactional
    public MemoDto processAction(
            UUID memoId,
            MemoApprovalActionDto dto,
            User currentUser,
            HttpServletRequest httpRequest) {

        Memo memo = findMemoById(memoId);

        // Validate memo is still pending
        if (memo.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Memo is no longer pending"
            );
        }

        // Comment mandatory on rejection
        if (dto.getAction() == StageStatus.REJECTED &&
                (dto.getComment() == null || dto.getComment().isBlank())) {
            throw new IllegalArgumentException(
                    "A comment is required when rejecting a memo"
            );
        }

        // Find the stage assigned to current user's role
        MemoApprovalStage stage = memoApprovalStageRepository
                .findByMemoAndAssignedRole(memo, currentUser.getRole())
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not an approver for this memo"
                ));

        if (stage.getStatus() != StageStatus.PENDING) {
            throw new IllegalArgumentException(
                    "You have already acted on this memo"
            );
        }

        // Update stage
        stage.setStatus(dto.getAction());
        stage.setComment(dto.getComment());
        stage.setActedBy(currentUser);
        stage.setActedAt(LocalDateTime.now());
        memoApprovalStageRepository.save(stage);

        // Notify author and other approvers
        notificationService.notifyMemoStageAction(memo, stage);

        if (dto.getAction() == StageStatus.REJECTED) {
            // One rejection = memo rejected immediately
            memo.setStatus(RequestStatus.REJECTED);
            memo.setRejectedBy(currentUser);
            memo.setRejectedAt(LocalDateTime.now());
            memo.setRejectionReason(dto.getComment());
            memoRepository.save(memo);

            notificationService.notifyMemoCompleted(memo);

            auditService.log(
                    currentUser,
                    ActionType.MEMO_REJECTED,
                    "MEMO",
                    memo.getId().toString(),
                    "MEMOS",
                    httpRequest
            );

        } else {
            // Check if all stages are now approved
            boolean allApproved = memoApprovalStageRepository
                    .areAllStagesApproved(memo);

            if (allApproved) {
                memo.setStatus(RequestStatus.APPROVED);
                memoRepository.save(memo);
                notificationService.notifyMemoCompleted(memo);
            }

            auditService.log(
                    currentUser,
                    ActionType.MEMO_APPROVED,
                    "MEMO",
                    memo.getId().toString(),
                    "MEMOS",
                    httpRequest
            );
        }

        return toDTO(memo);
    }

    // ─── Queries ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemoDto> getMyMemos(User user) {
        return memoRepository
                .findByAuthorOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MemoDto> getPendingMemosForRole(User user) {
        return memoRepository
                .findPendingMemosByRole(user.getRole())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MemoDto> getAllMemos() {
        return memoRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MemoDto getMemoById(UUID id) {
        return toDTO(findMemoById(id));
    }

    // ─── Helpers ──────────────────────────────────────────────

    private Memo findMemoById(UUID id) {
        return memoRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Memo not found: " + id));
    }

    private String generateReferenceNumber() {
        return memoRepository.findLatestReferenceNumber()
                .map(last -> {
                    int number = Integer.parseInt(
                            last.replace("MEMO-", "")
                    );
                    return String.format("MEMO-%03d", number + 1);
                })
                .orElse("MEMO-001");
    }

    // ─── DTO Mapper ───────────────────────────────────────────

    public MemoDto toDTO(Memo memo) {
        return MemoDto.builder()
                .id(memo.getId())
                .referenceNumber(memo.getReferenceNumber())
                .title(memo.getTitle())
                .content(memo.getContent())
                .authorId(memo.getAuthor().getId())
                .authorName(memo.getAuthor().getFullName())
                .status(memo.getStatus())
                .approvalStages(
                        memo.getApprovalStages().stream()
                                .map(this::toStageDTO)
                                .collect(Collectors.toList())
                )
                .rejectionReason(memo.getRejectionReason())
                .rejectedByName(
                        memo.getRejectedBy() != null
                                ? memo.getRejectedBy().getFullName()
                                : null
                )
                .rejectedAt(memo.getRejectedAt())
                .createdAt(memo.getCreatedAt())
                .updatedAt(memo.getUpdatedAt())
                .build();
    }

    private MemoDto.MemoApprovalStageDTO toStageDTO(
            MemoApprovalStage stage) {
        return MemoDto.MemoApprovalStageDTO.builder()
                .id(stage.getId())
                .assignedRole(stage.getAssignedRole())
                .actedByName(
                        stage.getActedBy() != null
                                ? stage.getActedBy().getFullName()
                                : null
                )
                .status(stage.getStatus())
                .comment(stage.getComment())
                .assignedAt(stage.getAssignedAt())
                .actedAt(stage.getActedAt())
                .build();
    }
}