package com.asa.asaunify.workflow;


import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.User;
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
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class EquipmentWorkflowStrategy implements WorkflowStrategy {

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

        // Stage 2 — Logistics checks stock
        // Created upfront but only becomes active after Stage 1 is approved
        stages.add(ApprovalStage.builder()
                .request(request)
                .stageIndex(2)
                .assignedRole(Role.LOGISTICS)
                .isParallel(false)
                .status(StageStatus.PENDING)
                .build());

        // Stage 3 — Procurement (only activated if logistics is out of stock)
        // Created upfront but only activated by WorkflowEngine when needed
        stages.add(ApprovalStage.builder()
                .request(request)
                .stageIndex(3)
                .assignedRole(Role.PROCUREMENT)
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

        // Stage was approved — decide what comes next
        int currentIndex = stage.getStageIndex();

        if (currentIndex == 1) {
            // Department Head approved → activate Logistics stage
            activateStage(request, 2);
            notificationService.notifyStageActivated(request, 2);

        } else if (currentIndex == 2) {
            // Logistics acted — check inStock field on the request
            Boolean inStock = (Boolean) request.getExtraField("in_stock");

            if (Boolean.TRUE.equals(inStock)) {
                // Logistics fulfilled it — request is complete
                request.setStatus(RequestStatus.COMPLETED);
                requestRepository.save(request);
                notificationService.notifyCompleted(request);

            } else {
                // Out of stock — activate Procurement stage
                activateStage(request, 3);
                notificationService.notifyStageActivated(request, 3);
            }

        } else if (currentIndex == 3) {
            // Procurement handled it — request is complete
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
        // Find the stage at this index and stamp its assignedAt
        // so the avg task time KPI starts counting from now
        List<ApprovalStage> stages = approvalStageRepository
                .findByRequestAndStageIndex(request, stageIndex);

        for (ApprovalStage s : stages) {
            s.setAssignedAt(java.time.LocalDateTime.now());
            approvalStageRepository.save(s);
        }
    }
}