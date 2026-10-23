package com.asa.asaunify.services;

import com.asa.asaunify.dtos.ApprovalActionDto;
import com.asa.asaunify.dtos.CreateRequestDto;
import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Department;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.exceptions.ResourceNotFoundException;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.RequestRepo;
import com.asa.asaunify.repos.UserRepo;
import com.asa.asaunify.repos.VehicleTripAssignmentRepo;
import com.asa.asaunify.workflow.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Who may cancel / approve what, and which requests are still actionable. */
class RequestServiceRulesTest {

    private RequestRepo requestRepo;
    private ApprovalStageRepo stageRepo;
    private WorkflowEngine engine;
    private NotificationService notifications;
    private RequestService service;

    private final Department sales = dept();
    private final Department hr = dept();

    private static Department dept() {
        Department d = new Department();
        d.setId(UUID.randomUUID());
        return d;
    }

    private static User user(Role role, Department d) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setRole(role);
        u.setDepartment(d);
        u.setActive(true);
        return u;
    }

    @BeforeEach
    void setUp() {
        requestRepo = mock(RequestRepo.class);
        stageRepo = mock(ApprovalStageRepo.class);
        engine = mock(WorkflowEngine.class);
        notifications = mock(NotificationService.class);
        service = new RequestService(requestRepo, stageRepo,
                mock(VehicleTripAssignmentRepo.class), mock(UserRepo.class),
                engine, notifications, mock(AuditService.class),
                mock(UserService.class), mock(LoanMatrixService.class));
    }

    private Request request(User initiator, Department d, RequestStatus status) {
        Request r = new Request();
        r.setId(UUID.randomUUID());
        r.setInitiator(initiator);
        r.setDepartment(d);
        r.setStatus(status);
        r.setApprovalStages(new ArrayList<>());
        when(requestRepo.findByIdForUpdate(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private ApprovalStage stage(Request r, int index, Role role) {
        ApprovalStage s = ApprovalStage.builder().request(r).stageIndex(index)
                .assignedRole(role).status(StageStatus.PENDING).build();
        r.getApprovalStages().add(s);
        return s;
    }

    private void currentStep(Request r, int index, ApprovalStage... open) {
        when(stageRepo.findCurrentStageIndex(r)).thenReturn(Optional.of(index));
        when(stageRepo.findByRequestAndStageIndex(r, index)).thenReturn(List.of(open));
    }

    private ApprovalActionDto approve() {
        ApprovalActionDto dto = new ApprovalActionDto();
        dto.setAction(StageStatus.APPROVED);
        return dto;
    }

    // ── Cancel ───────────────────────────────────────────────

    @Test
    void ownDepartmentHeadCancelsAPendingRequest() {
        Request r = request(user(Role.EMPLOYEE, sales), sales, RequestStatus.PENDING);
        service.cancelRequest(r.getId(), "no longer needed",
                user(Role.DEPARTMENT_HEAD, sales), null);
        assertEquals(RequestStatus.CANCELLED, r.getStatus());
        verify(notifications).notifyCancelled(r, "no longer needed");
    }

    @Test
    void approvedRequestsCanStillBeCancelled() {
        Request r = request(user(Role.EMPLOYEE, sales), sales, RequestStatus.APPROVED);
        service.cancelRequest(r.getId(), "changed plans", user(Role.ADMIN, null), null);
        assertEquals(RequestStatus.CANCELLED, r.getStatus());
    }

    @Test
    void otherDepartmentHeadInitiatorAndOtherRolesCannotCancel() {
        Request r = request(user(Role.EMPLOYEE, sales), sales, RequestStatus.PENDING);
        for (User u : List.of(user(Role.DEPARTMENT_HEAD, hr), r.getInitiator(),
                user(Role.RM, sales), user(Role.LOGISTICS, hr))) {
            assertThrows(ResourceNotFoundException.class,
                    () -> service.cancelRequest(r.getId(), "x", u, null));
        }
        assertEquals(RequestStatus.PENDING, r.getStatus());
    }

    @Test
    void finishedRequestsCannotBeCancelled() {
        for (RequestStatus s : List.of(RequestStatus.COMPLETED,
                RequestStatus.REJECTED, RequestStatus.CANCELLED, RequestStatus.DRAFT)) {
            Request r = request(user(Role.EMPLOYEE, sales), sales, s);
            assertThrows(IllegalArgumentException.class, () ->
                    service.cancelRequest(r.getId(), "x", user(Role.ADMIN, null), null));
            assertEquals(s, r.getStatus());
        }
    }

    @Test
    void cancellingNeedsAReason() {
        Request r = request(user(Role.EMPLOYEE, sales), sales, RequestStatus.PENDING);
        assertThrows(IllegalArgumentException.class, () ->
                service.cancelRequest(r.getId(), "  ", user(Role.ADMIN, null), null));
    }

    // ── Approve / reject ─────────────────────────────────────

    @Test
    void cancelledWorkflowCannotBeActedOn() {
        Request r = request(user(Role.EMPLOYEE, sales), sales, RequestStatus.CANCELLED);
        ApprovalStage s = stage(r, 1, Role.DEPARTMENT_HEAD);
        currentStep(r, 1, s);
        assertThrows(IllegalArgumentException.class, () -> service.processAction(
                r.getId(), approve(), user(Role.DEPARTMENT_HEAD, sales), null));
        verify(engine, never()).processAction(any(), any(), any(), any());
    }

    @Test
    void onlyTheInitiatingDepartmentsHeadMayActOnTheDepartmentStage() {
        Request r = request(user(Role.EMPLOYEE, sales), sales, RequestStatus.PENDING);
        ApprovalStage s = stage(r, 1, Role.DEPARTMENT_HEAD);
        currentStep(r, 1, s);
        when(requestRepo.findById(r.getId())).thenReturn(Optional.of(r));

        assertThrows(IllegalArgumentException.class, () -> service.processAction(
                r.getId(), approve(), user(Role.DEPARTMENT_HEAD, hr), null));
        verify(engine, never()).processAction(any(), any(), any(), any());

        User head = user(Role.DEPARTMENT_HEAD, sales);
        service.processAction(r.getId(), approve(), head, null);
        verify(engine).processAction(r, s, StageStatus.APPROVED, null);
        assertEquals(head, s.getActedBy());
    }

    @Test
    void laterDepartmentsActByRoleAndMustHaveBeenReached() {
        Request r = request(user(Role.EMPLOYEE, sales), sales, RequestStatus.PENDING);
        ApprovalStage s1 = stage(r, 1, Role.DEPARTMENT_HEAD);
        ApprovalStage s2 = stage(r, 2, Role.LOGISTICS);
        currentStep(r, 1, s1);   // logistics step not reached yet
        when(requestRepo.findById(r.getId())).thenReturn(Optional.of(r));

        assertThrows(IllegalArgumentException.class, () -> service.processAction(
                r.getId(), approve(), user(Role.LOGISTICS, hr), null));

        s1.setStatus(StageStatus.APPROVED);
        currentStep(r, 2, s2);
        service.processAction(r.getId(), approve(), user(Role.LOGISTICS, hr), null);
        verify(engine).processAction(r, s2, StageStatus.APPROVED, null);
    }

    @Test
    void adminResolvesEveryOpenStageOfTheStep() {
        Request r = request(user(Role.LOAN_OFFICER, sales), sales, RequestStatus.PENDING);
        ApprovalStage a = stage(r, 1, Role.MSME_OFFICER);
        ApprovalStage b = stage(r, 1, Role.RM);
        ApprovalStage c = stage(r, 1, Role.CREDIT_OFFICER);
        currentStep(r, 1, a, b, c);
        when(requestRepo.findById(r.getId())).thenReturn(Optional.of(r));

        service.processAction(r.getId(), approve(), user(Role.ADMIN, null), null);
        verify(engine, times(3)).processAction(any(), any(), any(), any());
    }

    @Test
    void loanApproverActsOnlyOnTheirOwnStage() {
        Request r = request(user(Role.LOAN_OFFICER, sales), sales, RequestStatus.PENDING);
        ApprovalStage a = stage(r, 1, Role.MSME_OFFICER);
        ApprovalStage b = stage(r, 1, Role.RM);
        currentStep(r, 1, a, b);
        when(requestRepo.findById(r.getId())).thenReturn(Optional.of(r));

        service.processAction(r.getId(), approve(), user(Role.RM, hr), null);
        verify(engine, times(1)).processAction(any(), any(), any(), any());
        verify(engine).processAction(r, b, StageStatus.APPROVED, null);
    }

    // ── Loan initiation ──────────────────────────────────────

    @Test
    void onlyLoanOfficersMayStartLoans() {
        CreateRequestDto dto = new CreateRequestDto();
        dto.setType(RequestType.LOAN);
        dto.setTitle("t");
        dto.setExtraFields(Map.of("amount", 3000000));
        for (Role role : List.of(Role.RM, Role.MSME_OFFICER, Role.EMPLOYEE,
                Role.DEPARTMENT_HEAD)) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> service.createDraft(dto, user(role, sales), null));
            assertEquals("Only Loan Officers can create Loan requests", e.getMessage());
        }
    }
}
