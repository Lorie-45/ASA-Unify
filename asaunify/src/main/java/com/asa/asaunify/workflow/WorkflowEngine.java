package com.asa.asaunify.workflow;


import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.RequestRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngine {

    private final EquipmentWorkflowStrategy equipmentStrategy;
    private final VehicleWorkflowStrategy vehicleStrategy;
    private final LoanWorkflowStrategy loanStrategy;
    private final ApprovalStageRepo approvalStageRepository;
    private final RequestRepo requestRepository;

    // ─── Called at submission time ────────────────────────────

    // Builds and saves all approval stages for a request
    // and sets the request status to PENDING
    @Transactional
    public List<ApprovalStage> initializeWorkflow(Request request) {
        // Set request to PENDING
        request.setStatus(RequestStatus.PENDING);
        requestRepository.save(request);

        // Build stages using the correct strategy
        List<ApprovalStage> stages = resolveStrategy(request.getType())
                .buildStages(request);

        log.info("Workflow initialized for request {} with {} stages",
                request.getCaseId(), stages.size());

        return stages;
    }

    // ─── Called when an approver acts ────────────────────────

    // Processes an approval or rejection action on a stage
    @Transactional
    public void processAction(
            Request request,
            ApprovalStage stage,
            StageStatus action,
            String comment) {

        // Update the stage itself
        stage.setStatus(action);
        stage.setComment(comment);
        stage.setActedAt(LocalDateTime.now());
        approvalStageRepository.save(stage);

        log.info("Stage {} on request {} acted on: {}",
                stage.getStageIndex(),
                request.getCaseId(),
                action);

        // Delegate next steps to the correct strategy
        resolveStrategy(request.getType())
                .onStageAction(request, stage, action);
    }

    // ─── Called by Logistics specifically ────────────────────

    // Handles the logistics stock decision
    // Sets in_stock in extraFields then processes as normal approval
    @Transactional
    public void processLogisticsAction(
            Request request,
            ApprovalStage stage,
            boolean inStock,
            String comment) {

        // Store the stock decision in the request's extraFields
        request.getExtraFields().put("in_stock", inStock);
        requestRepository.save(request);

        // Then process as a normal approval
        processAction(request, stage, StageStatus.APPROVED, comment);
    }

    // ─── Stage visibility check ───────────────────────────────

    // Returns true if a stage at a given index is currently active
    // Used to determine if an approver should see this stage
    public boolean isStageActive(Request request, int stageIndex) {
        // For parallel stages (loans) — all active at once
        // For sequential — only the current minimum pending index
        boolean isParallel = request.getApprovalStages().stream()
                .anyMatch(s -> s.getStageIndex() == stageIndex
                        && s.isParallel());

        if (isParallel) return true;

        Integer currentIndex = approvalStageRepository
                .findCurrentStageIndex(request)
                .orElse(-1);

        return currentIndex == stageIndex;
    }

    // ─── Helper ──────────────────────────────────────────────

    private WorkflowStrategy resolveStrategy(RequestType type) {
        return switch (type) {
            case EQUIPMENT -> equipmentStrategy;
            case VEHICLE -> vehicleStrategy;
            case LOAN -> loanStrategy;
        };
    }
}