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

        // Get all parallel stages for this request
        List<ApprovalStage> allStages = approvalStageRepository
                .findByRequestOrderByStageIndexAsc(request);

        long totalStages = allStages.size();
        long actedStages = allStages.stream()
                .filter(s -> s.getStatus() != StageStatus.PENDING)
                .count();
        long rejectedStages = allStages.stream()
                .filter(s -> s.getStatus() == StageStatus.REJECTED)
                .count();

        log.info("Loan parallel stages: total={} acted={} rejected={}",
                totalStages, actedStages, rejectedStages);

        // Not all approvers have acted yet — wait for the rest
        if (actedStages < totalStages) {
            log.info("Waiting for remaining {} approvers to act",
                    totalStages - actedStages);
            return;
        }

        // All have acted — determine final outcome
        if (rejectedStages > 0) {
            request.setStatus(RequestStatus.REJECTED);
            requestRepository.save(request);
            notificationService.notifyRejection(request, stage);
            log.info("Loan {} rejected — {}/{} rejections",
                    request.getCaseId(), rejectedStages, totalStages);
        } else {
            request.setStatus(RequestStatus.APPROVED);
            requestRepository.save(request);
            notificationService.notifyApproved(request);
            log.info("Loan {} approved unanimously", request.getCaseId());
        }
    }

    @Override
    public boolean isComplete(Request request) {
        return request.getStatus() == RequestStatus.COMPLETED;
    }
}