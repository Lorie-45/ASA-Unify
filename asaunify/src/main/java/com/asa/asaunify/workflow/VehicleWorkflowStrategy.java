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

        if (action == StageStatus.REJECTED) {
            // Reject the entire request
            request.setStatus(RequestStatus.REJECTED);
            requestRepository.save(request);

            // Notify everyone involved
            notificationService.notifyRejection(request, stage);
            return;
        }

        int currentIndex = stage.getStageIndex();

        if (currentIndex == 1) {
            // Department Head approved → activate Fleet Manager stage
            activateStage(request, 2);
            notificationService.notifyStageActivated(request, 2);

        } else if (currentIndex == 2) {
            // Fleet Manager completed trip planning
            // Request moves to COMPLETED after driver is assigned
            // Driver assignment is handled separately in VehicleRequestService
            request.setStatus(RequestStatus.COMPLETED);
            requestRepository.save(request);
            notificationService.notifyCompleted(request);
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