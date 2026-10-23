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

        // Sequential chain: a rejection at any stage ends the request now, so
        // a Department Head's rejection cannot be overridden by a later stage.
        if (action == StageStatus.REJECTED) {
            request.setStatus(RequestStatus.REJECTED);
            requestRepository.save(request);
            notificationService.notifyRejection(request, stage);
            log.info("Vehicle request {} rejected at stage {}",
                    request.getCaseId(), stage.getStageIndex());
            return;
        }

        if (stage.getStageIndex() == 1) {
            // Department Head approved -> hand over to the Fleet Manager
            activateStage(request, 2);
            notificationService.notifyStageActivated(request, 2);
        } else {
            // Fleet Manager approved -> ready for driver assignment
            request.setStatus(RequestStatus.APPROVED);
            requestRepository.save(request);
            notificationService.notifyApproved(request);
            log.info("Vehicle request {} approved", request.getCaseId());
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