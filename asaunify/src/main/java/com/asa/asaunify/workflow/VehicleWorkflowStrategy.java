package com.asa.asaunify.workflow;



import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.RequestRepo;
import com.asa.asaunify.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleWorkflowStrategy implements WorkflowStrategy {

    private final ApprovalStageRepo approvalStageRepository;
    private final RequestRepo requestRepository;
    private final NotificationService notificationService;

    @Override
    public List<ApprovalStage> buildStages(Request request) {
        List<ApprovalStage> stages = new ArrayList<>();

        // Stage 1 — Department Head approves or rejects
        stages.add(ApprovalStage.builder()
                .request(request)
                .stageIndex(1)
                .assignedRole(Role.DEPARTMENT_HEAD)
                .isParallel(false)
                .status(StageStatus.PENDING)
                .build());

        // Stage 2 — Fleet Manager plans trip and assigns driver
        stages.add(ApprovalStage.builder()
                .request(request)
                .stageIndex(2)
                .assignedRole(Role.FLEET_MANAGER)
                .isParallel(false)
                .status(StageStatus.PENDING)
                .build());

        return approvalStageRepository.saveAll(stages);
    }

    @Override
    public void onStageAction(
            Request request,
            ApprovalStage stage,
            StageStatus action) {

        // Get all parallel stages for this loan request
        List<ApprovalStage> allStages = approvalStageRepository
                .findByRequestOrderByStageIndexAsc(request);

        long totalStages = allStages.size();
        long actedStages = allStages.stream()
                .filter(s -> s.getStatus() != StageStatus.PENDING)
                .count();
        long approvedStages = allStages.stream()
                .filter(s -> s.getStatus() == StageStatus.APPROVED)
                .count();
        long rejectedStages = allStages.stream()
                .filter(s -> s.getStatus() == StageStatus.REJECTED)
                .count();

        log.info("Loan stages: total={} acted={} approved={} rejected={}",
                totalStages, actedStages, approvedStages, rejectedStages);

        // Not all approvers have acted yet — wait
        if (actedStages < totalStages) {
            log.info("Not all approvers have acted yet — waiting");
            return;
        }

        // All have acted — determine outcome
        if (rejectedStages > 0) {
            // At least one rejection — request is rejected
            request.setStatus(RequestStatus.REJECTED);

            // Find the first rejector for the rejection reason
            ApprovalStage firstRejection = allStages.stream()
                    .filter(s -> s.getStatus() == StageStatus.REJECTED)
                    .findFirst()
                    .orElse(stage);

            request.setRejectedAt(LocalDateTime.now());
            requestRepository.save(request);
            notificationService.notifyRejection(request, firstRejection);

            log.info("Loan request {} rejected — {} of {} approvers rejected",
                    request.getCaseId(), rejectedStages, totalStages);

        } else if (approvedStages == totalStages) {
            // All approved — request is approved
            request.setStatus(RequestStatus.APPROVED);
            requestRepository.save(request);
            notificationService.notifyApproved(request);

            log.info("Loan request {} approved unanimously",
                    request.getCaseId());
        }
    }

    @Override
    public boolean isComplete(Request request) {
        return request.getStatus() == RequestStatus.COMPLETED;
    }

    // ─── Helper ──────────────────────────────────────────────

    private void activateStage(Request request, int stageIndex) {
        List<ApprovalStage> stages = approvalStageRepository
                .findByRequestAndStageIndex(request, stageIndex);

        for (ApprovalStage s : stages) {
            s.setAssignedAt(java.time.LocalDateTime.now());
            approvalStageRepository.save(s);
        }
    }
}