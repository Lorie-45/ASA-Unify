package com.asa.asaunify.workflow;


import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.RequestRepo;
import com.asa.asaunify.services.LoanMatrixService;
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
public class LoanWorkflowStrategy implements WorkflowStrategy {

    private final ApprovalStageRepo approvalStageRepository;
    private final RequestRepo requestRepository;
    private final NotificationService notificationService;
    private final LoanMatrixService loanMatrixService;

    @Override
    public List<ApprovalStage> buildStages(Request request) {
        // Resolve approvers from the loan matrix
        // based on amount stored in extraFields
        List<Role> approvers = loanMatrixService.resolveApprovers(request);

        List<ApprovalStage> stages = new ArrayList<>();

        // All loan approvers share stageIndex = 1
        // because they all act in parallel simultaneously
        for (Role role : approvers) {
            stages.add(ApprovalStage.builder()
                    .request(request)
                    .stageIndex(1)
                    .assignedRole(role)
                    .isParallel(true)
                    .status(StageStatus.PENDING)
                    .assignedAt(LocalDateTime.now())
                    .build());
        }

        return approvalStageRepository.saveAll(stages);
    }

    @Override
    public void onStageAction(
            Request request,
            ApprovalStage stage,
            StageStatus action) {

        if (action == StageStatus.REJECTED) {
            // One rejection = entire loan request rejected immediately
            request.setStatus(RequestStatus.REJECTED);
            requestRepository.save(request);
            notificationService.notifyRejection(request, stage);
            return;
        }

        // Stage approved — check if ALL parallel stages are now approved
        boolean allApproved = approvalStageRepository
                .areAllStagesApproved(request);

        if (allApproved) {
            // Every approver has approved — loan request is complete
            request.setStatus(RequestStatus.COMPLETED);
            requestRepository.save(request);
            notificationService.notifyCompleted(request);
        }
        // If not all approved yet — do nothing, wait for remaining approvers
    }

    @Override
    public boolean isComplete(Request request) {
        return request.getStatus() == RequestStatus.COMPLETED;
    }
}