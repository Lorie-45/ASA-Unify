package com.asa.asaunify.workflow;

import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.RequestRepo;
import com.asa.asaunify.services.NotificationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import org.springframework.transaction.annotation.Transactional;

import com.asa.asaunify.services.PasswordResetService;
import com.asa.asaunify.dtos.auth.ResetPasswordRequest;
import com.asa.asaunify.dtos.auth.VerifyResetCodeRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VehicleWorkflowStrategyTest {

    private final ApprovalStageRepo stageRepo = mock(ApprovalStageRepo.class);
    private final RequestRepo requestRepo = mock(RequestRepo.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final VehicleWorkflowStrategy strategy =
            new VehicleWorkflowStrategy(stageRepo, requestRepo, notifications);

    private ApprovalStage stage(int index) {
        return ApprovalStage.builder().stageIndex(index).build();
    }

    @Test
    void departmentHeadRejectionRejectsTheRequestImmediately() {
        Request request = new Request();
        request.setStatus(RequestStatus.PENDING);

        strategy.onStageAction(request, stage(1), StageStatus.REJECTED);

        assertEquals(RequestStatus.REJECTED, request.getStatus());
        verify(notifications).notifyRejection(any(), any());
    }

    @Test
    void departmentHeadApprovalKeepsRequestPendingForFleetManager() {
        Request request = new Request();
        request.setStatus(RequestStatus.PENDING);

        strategy.onStageAction(request, stage(1), StageStatus.APPROVED);

        assertEquals(RequestStatus.PENDING, request.getStatus());
        verify(notifications, never()).notifyApproved(any());
    }

    @Test
    void fleetManagerApprovalApprovesTheRequest() {
        Request request = new Request();
        request.setStatus(RequestStatus.PENDING);

        strategy.onStageAction(request, stage(2), StageStatus.APPROVED);

        assertEquals(RequestStatus.APPROVED, request.getStatus());
        verify(notifications).notifyApproved(request);
    }

    // Regression: a rejected code must keep its attempt counter, so the
    // exception may not roll the transaction back.
    @Test
    void resetCodeChecksDoNotRollBackOnRejection() throws Exception {
        Method verify = PasswordResetService.class
                .getMethod("verifyCode", VerifyResetCodeRequest.class);
        Method reset = PasswordResetService.class.getMethod("resetPassword",
                ResetPasswordRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);

        for (Method m : new Method[]{verify, reset}) {
            Transactional tx = m.getAnnotation(Transactional.class);
            assertTrue(java.util.Arrays.asList(tx.noRollbackFor())
                    .contains(IllegalArgumentException.class), m.getName());
        }
    }
}
