package com.asa.asaunify.services;



import com.asa.asaunify.dtos.*;
import com.asa.asaunify.entity.*;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.exceptions.ResourceNotFoundException;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.*;
import com.asa.asaunify.workflow.WorkflowEngine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestService {

    private final RequestRepo requestRepository;
    private final ApprovalStageRepo approvalStageRepository;
    private final VehicleTripAssignmentRepo tripAssignmentRepository;
    private final UserRepo userRepository;
    private final WorkflowEngine workflowEngine;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UserService userService;
    private final LoanMatrixService loanMatrixService;

    // ─── Create (Save as Draft) ───────────────────────────────

    @Transactional
    public RequestResponseDto createDraft(
            CreateRequestDto dto,
            User initiator,
            HttpServletRequest httpRequest) {

        validateCanInitiate(initiator);

        if (initiator.getDepartment() == null) {
            throw new IllegalArgumentException(
                    "You must be assigned to a department before creating a request"
            );
        }

        validateExtraFields(dto);
        validateRoleForRequestType(initiator.getRole(), dto.getType());

        Request request = Request.builder()
                .caseId(generateReferenceNumber())
                .type(dto.getType())
                .status(RequestStatus.DRAFT)
                .title(dto.getTitle())
                .details(dto.getDetails())
                .notes(dto.getNotes())
                .dueDate(dto.getDueDate())
                .extraFields(dto.getExtraFields())
                .initiator(initiator)
                .department(initiator.getDepartment())
                .build();

        // Handle loan top-up parent link
        if (dto.getParentRequestId() != null) {
            Request parent = requestRepository
                    .findById(dto.getParentRequestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent request not found"));
            validateTopUp(parent, dto);
            request.setParentRequest(parent);
        }

        Request saved = requestRepository.save(request);

        auditService.log(
                initiator,
                ActionType.REQUEST_CREATED,
                "REQUEST",
                saved.getId().toString(),
                "REQUESTS",
                httpRequest
        );

        return toDTO(saved);
    }

    // ─── Submit (Draft → Pending) ─────────────────────────────

    @Transactional
    public RequestResponseDto submitRequest(
            UUID requestId,
            User currentUser,
            HttpServletRequest httpRequest) {

        Request request = findRequestById(requestId);

        // Only the initiator can submit their own draft
        if (!request.getInitiator().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException(
                    "Only the initiator can submit this request"
            );
        }

        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Only draft requests can be submitted"
            );
        }

        // Initialize workflow — builds stages, sets status to PENDING
        workflowEngine.initializeWorkflow(request);

        // Notify first stage assignees
        notificationService.notifyRequestSubmitted(request);

        auditService.log(
                currentUser,
                ActionType.REQUEST_SUBMITTED,
                "REQUEST",
                request.getId().toString(),
                "REQUESTS",
                httpRequest
        );

        return toDTO(requestRepository.findById(requestId).orElseThrow());
    }

    // ─── Approve / Reject ─────────────────────────────────────

    @Transactional
    public RequestResponseDto processAction(
            UUID requestId,
            ApprovalActionDto dto,
            User currentUser,
            HttpServletRequest httpRequest) {

        // Row lock: two approvers acting at once must not both read the other
        // as "still pending", which would leave a parallel request stuck.
        Request request = findRequestForUpdate(requestId);

        // Validate comment is provided on rejection
        if (dto.getAction() == StageStatus.REJECTED &&
                (dto.getComment() == null || dto.getComment().isBlank())) {
            throw new IllegalArgumentException(
                    "A comment is required when rejecting a request"
            );
        }

        // Only a live (PENDING) request can be acted on. A cancelled, rejected
        // or completed workflow is closed for good - this also stops a stage
        // that was left open by a cancellation from being approved later.
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Request is not in a pending state"
            );
        }

        // The stage(s) this user may act on: their own role's stage (for a
        // department head, only on requests of their own department), or every
        // open stage of the current step for an admin.
        List<ApprovalStage> stages = findActionableStages(request, currentUser);

        for (ApprovalStage stage : stages) {
            stage.setActedBy(currentUser);

            if (stage.getAssignedRole() == Role.LOGISTICS &&
                    stage.getStageIndex() == 2 &&
                    dto.getAction() == StageStatus.APPROVED &&
                    dto.getInStock() != null) {

                workflowEngine.processLogisticsAction(
                        request, stage, dto.getInStock(), dto.getComment()
                );

            } else {
                workflowEngine.processAction(
                        request, stage, dto.getAction(), dto.getComment()
                );
            }
        }

        // Refresh request after workflow engine processed it
        Request updatedRequest = requestRepository
                .findById(requestId).orElseThrow();

        // Only stamp rejection fields if:
        // 1. The workflow engine has finalized the request as REJECTED
        // 2. The current user is the one who triggered the rejection
        // For parallel loans — only stamp when ALL have acted and outcome is REJECTED
        if (updatedRequest.getStatus() == RequestStatus.REJECTED &&
                dto.getAction() == StageStatus.REJECTED &&
                updatedRequest.getRejectedBy() == null) { // ← don't overwrite if already set
            updatedRequest.setRejectedBy(currentUser);
            updatedRequest.setRejectedAt(LocalDateTime.now());
            updatedRequest.setRejectionReason(dto.getComment());
            requestRepository.save(updatedRequest);
        }

        auditService.log(
                currentUser,
                dto.getAction() == StageStatus.APPROVED
                        ? ActionType.STAGE_APPROVED
                        : ActionType.STAGE_REJECTED,
                "REQUEST",
                request.getId().toString(),
                "REQUESTS",
                httpRequest
        );

        return toDTO(requestRepository.findById(requestId).orElseThrow());
    }

    // ─── Cancel (Admin / head of the request's department) ────

    @Transactional
    public RequestResponseDto cancelRequest(
            UUID requestId,
            String reason,
            User currentUser,
            HttpServletRequest httpRequest) {

        Request request = findRequestForUpdate(requestId);

        if (!canCancel(request, currentUser)) {
            // Same answer as a miss: no existence disclosure.
            throw new ResourceNotFoundException("Request not found");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "A reason is required to cancel a request"
            );
        }

        // Approved requests can still be cancelled; anything already finished
        // (or never started) cannot.
        if (request.getStatus() != RequestStatus.PENDING &&
                request.getStatus() != RequestStatus.APPROVED) {
            throw new IllegalArgumentException(
                    request.getStatus() == RequestStatus.DRAFT
                            ? "Drafts are deleted, not cancelled"
                            : "Only pending or approved requests can be cancelled"
            );
        }

        // CANCELLED ends the workflow: processAction and the pending lists
        // both require PENDING, so no remaining stage can be actioned.
        request.setStatus(RequestStatus.CANCELLED);
        request.setRejectedBy(currentUser);
        request.setRejectedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        requestRepository.save(request);

        notificationService.notifyCancelled(request, reason);

        auditService.log(
                currentUser,
                ActionType.REQUEST_CANCELLED,
                "REQUEST",
                request.getId().toString(),
                "REQUESTS",
                httpRequest
        );

        return toDTO(request);
    }

    // Admins, and the head of the department the request belongs to.
    private boolean canCancel(Request request, User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        return user.getRole() == Role.DEPARTMENT_HEAD
                && user.getDepartment() != null
                && request.getDepartment() != null
                && request.getDepartment().getId()
                        .equals(user.getDepartment().getId());
    }

    @Transactional(readOnly = true)
    public List<RequestResponseDto> getLoanRequests(User user) {
        if (user.getRole() == Role.ADMIN
                || user.getRole() == Role.RM
                || user.getRole() == Role.MSME_OFFICER
                || user.getRole() == Role.CREDIT_OFFICER) {
            // Admin and the loan approvers see every loan: approval is by role,
            // not by the initiator's department.
            return requestRepository.findByType(RequestType.LOAN)
                    .stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
        // Others see loan requests from their department
        if (user.getDepartment() == null) {
            return List.of();
        }
        return requestRepository.findByTypeAndDepartment(
                        RequestType.LOAN,
                        user.getDepartment()
                )
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ─── Assign Driver (Fleet Manager only) ───────────────────

    @Transactional
    public void assignDriver(
            UUID requestId,
            AssignDriverDto dto,
            User currentUser,
            HttpServletRequest httpRequest) {

        // Locked so a double-submit cannot create two assignments.
        Request request = findRequestForUpdate(requestId);

        // Only Fleet Manager can assign drivers
        if (currentUser.getRole() != Role.FLEET_MANAGER) {
            throw new IllegalArgumentException("Only Fleet Managers can assign drivers");
        }

        // Only APPROVED vehicle requests can have drivers assigned
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new IllegalArgumentException(
                    "Driver can only be assigned to an approved vehicle request"
            );
        }

        if (request.getType() != RequestType.VEHICLE) {
            throw new IllegalArgumentException(
                    "Driver assignment is only for vehicle requests"
            );
        }

        User driver = userRepository.findById(dto.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));

        if (driver.getRole() != Role.DRIVER) {
            throw new IllegalArgumentException("Selected user is not a driver");
        }
        if (!driver.isActive()) {
            throw new IllegalArgumentException("Selected driver is not active");
        }

        // Create the trip assignment
        VehicleTripAssignment assignment = VehicleTripAssignment.builder()
                .request(request)
                .driver(driver)
                .assignedBy(currentUser)
                .assignedAt(LocalDateTime.now())
//                .note(dto.getNote())
                .build();

        tripAssignmentRepository.save(assignment);

        // Now mark the request as COMPLETED
        request.setStatus(RequestStatus.COMPLETED);
        requestRepository.save(request);

        // Notify driver
        notificationService.notifyDriverAssigned(request, driver);

        auditService.log(
                currentUser,
                ActionType.DRIVER_ASSIGNED,
                "REQUEST",
                requestId.toString(),
                "REQUESTS",
                httpRequest
        );
    }

    // ─── Driver marks trip as seen ────────────────────────────

    @Transactional
    public void markTripSeen(UUID requestId, User driver) {
        Request request = findRequestById(requestId);

        VehicleTripAssignment assignment = tripAssignmentRepository
                .findByRequest(request)
                .orElseThrow(() ->
                        new IllegalArgumentException("No trip assignment found"));

        if (!assignment.getDriver().getId().equals(driver.getId())) {
            throw new IllegalArgumentException(
                    "This trip is not assigned to you"
            );
        }

        if (assignment.getSeenAt() == null) {
            assignment.setSeenAt(LocalDateTime.now());
            tripAssignmentRepository.save(assignment);
        }
    }

    @Transactional(readOnly = true)
    public List<VehicleTripAssignmentDto> getMyTrips(User driver) {
        return tripAssignmentRepository.findByDriver(driver)
                .stream()
                .map(this::toTripDTO)
                .collect(Collectors.toList());
    }

    private VehicleTripAssignmentDto toTripDTO(VehicleTripAssignment assignment) {
        Request request = assignment.getRequest();
        Map<String, Object> extra = request.getExtraFields() != null
                ? request.getExtraFields()
                : Map.of();

        return VehicleTripAssignmentDto.builder()
                .id(assignment.getId())
                .requestId(request.getId())
                .requestReferenceNumber(request.getCaseId())
                .requestTitle(request.getTitle())
                .initiatorName(request.getInitiator().getFullName())
                .destination(extra.getOrDefault("destination", "").toString())
                .tripDate(extra.getOrDefault("trip_date", "").toString())
                .purpose(extra.getOrDefault("purpose", "").toString())
                .assignedByName(assignment.getAssignedBy().getFullName())
                .assignedAt(assignment.getAssignedAt())
                .seenAt(assignment.getSeenAt())
//                .note(assignment.getNote())
                .requestStatus(request.getStatus().name())
                .build();
    }


    // ─── Queries ──────────────────────────────────────────────

    // My requests — initiator view
    @Transactional(readOnly = true)
    public List<RequestResponseDto> getMyRequests(User user) {
        return requestRepository
                .findByInitiatorOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Department requests — department head view
    @Transactional(readOnly = true)
    public List<RequestResponseDto> getDepartmentRequests(User deptHead) {
        if (deptHead.getDepartment() == null) {
            // Admins/auditors have no department and oversee everything;
            // anyone else without one has nothing to show.
            if (deptHead.getRole() == Role.ADMIN
                    || deptHead.getRole() == Role.AUDITOR) {
                return getAllRequests();
            }
            return List.of();
        }
        return requestRepository
                .findByDepartmentOrderByCreatedAtDesc(deptHead.getDepartment())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Pending approvals — what is waiting on current user's role
//    @Transactional(readOnly = true)
//    public List<RequestResponseDto> getPendingForRole(User user) {
//        log.info("=== getPendingForRole ===");
//        log.info("User role: {}", user.getRole());
//
//        List<ApprovalStage> stages = approvalStageRepository
//                .findByAssignedRoleAndStatus(user.getRole(), StageStatus.PENDING);
//
//        log.info("Found {} pending stages for role {}", stages.size(), user.getRole());
//        stages.forEach(s -> log.info("Stage: {} request: {} status: {}",
//                s.getId(), s.getRequest().getId(), s.getStatus()));
//
//        return approvalStageRepository
//                .findByAssignedRoleAndStatus(user.getRole(), StageStatus.PENDING)
//                .stream()
//                .map(ApprovalStage::getRequest)
//                .filter(r -> r.getStatus() == RequestStatus.PENDING)
//                .map(this::toDTO)
//                .collect(Collectors.toList());
//    }

//    @Transactional(readOnly = true)
//    public List<RequestResponseDto> getPendingForRole(User user) {
//        try {
//            log.info("=== getPendingForRole START ===");
//            log.info("User: {} Role: {}", user.getEmail(), user.getRole());
//
//            List<ApprovalStage> stages = approvalStageRepository
//                    .findByAssignedRoleAndStatus(user.getRole(), StageStatus.PENDING);
//
//            log.info("Found {} stages", stages.size());
//
//            List<RequestResponseDto> result = stages.stream()
//                    .map(ApprovalStage::getRequest)
//                    .filter(r -> r.getStatus() == RequestStatus.PENDING)
//                    .map(this::toDTO)
//                    .collect(Collectors.toList());
//
//            log.info("Returning {} results", result.size());
//            return result;
//
//        } catch (Exception e) {
//            log.error("=== getPendingForRole FAILED ===", e);
//            return List.of();
//        }
//    }


    @Transactional(readOnly = true)
    public List<RequestResponseDto> getPendingForRole(User user) {
        if (user.getRole() == Role.ADMIN) {
            return requestRepository.findAll().stream()
                    .filter(r -> r.getStatus() == RequestStatus.PENDING)
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }

        return approvalStageRepository
                .findByAssignedRoleAndStatus(user.getRole(), StageStatus.PENDING)
                .stream()
                .filter(stage -> stage.getRequest().getStatus() == RequestStatus.PENDING)
                // Only requests that have actually reached this user's step.
                .filter(stage -> stage.getStageIndex() == currentStepOf(stage.getRequest()))
                .filter(stage -> canActOnStage(stage, stage.getRequest(), user))
                .map(ApprovalStage::getRequest)
                .distinct()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Lowest step that still has an open stage on this request.
    private int currentStepOf(Request request) {
        return request.getApprovalStages().stream()
                .filter(s -> s.getStatus() == StageStatus.PENDING
                        && s.getActedAt() == null)
                .mapToInt(ApprovalStage::getStageIndex)
                .min()
                .orElse(-1);
    }

    // Role must match; a department head's stage belongs to the head of the
    // department the request came from, not to every department head.
    private boolean canActOnStage(ApprovalStage stage, Request request, User user) {
        if (stage.getAssignedRole() != user.getRole()) {
            return false;
        }
        if (stage.getAssignedRole() == Role.DEPARTMENT_HEAD) {
            return user.getDepartment() != null
                    && request.getDepartment() != null
                    && user.getDepartment().getId()
                            .equals(request.getDepartment().getId());
        }
        return true;
    }

    // All requests — admin/auditor view
    @Transactional(readOnly = true)
    public List<RequestResponseDto> getAllRequests() {
        return requestRepository
                .findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RequestResponseDto getRequestById(UUID id, User currentUser) {
        Request request = findRequestById(id);
        if (!canView(request, currentUser)) {
            // Same response as a genuine miss so callers cannot probe
            // which request IDs exist (no existence disclosure).
            throw new ResourceNotFoundException("Request not found");
        }
        return toDTO(request);
    }

    // Object-level read authorization for a single request.
    // A user may view a request only if they are:
    //   • ADMIN or AUDITOR (system-wide oversight), or
    //   • the initiator, or
    //   • in the same department as the request (dept head / dept members), or
    //   • an approver whose role is assigned to one of its stages.
    private boolean canView(Request request, User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.AUDITOR) {
            return true;
        }
        if (request.getInitiator() != null
                && request.getInitiator().getId().equals(user.getId())) {
            return true;
        }
        if (user.getDepartment() != null
                && request.getDepartment() != null
                && request.getDepartment().getId().equals(user.getDepartment().getId())) {
            return true;
        }
        return request.getApprovalStages() != null
                && request.getApprovalStages().stream()
                        .anyMatch(s -> s.getAssignedRole() == user.getRole());
    }

    @Transactional
    public RequestResponseDto updateDraft(
            UUID id,
            UpdateRequestDto dto,
            User currentUser,
            HttpServletRequest httpRequest) {

        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        // Only the initiator can edit their own draft
        if (!request.getInitiator().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("You can only edit your own requests");
        }

        // Can only edit drafts
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft requests can be edited");
        }

        if (dto.getTitle() != null) request.setTitle(dto.getTitle());
        if (dto.getDetails() != null) request.setDetails(dto.getDetails());
        if (dto.getNotes() != null) request.setNotes(dto.getNotes());

        if (dto.getExtraFields() != null) {
            // Editing a draft must honour the same loan bounds as creating one,
            // otherwise an out-of-range amount can be written back in.
            if (request.getType() == RequestType.LOAN) {
                Object rawAmount = dto.getExtraFields().get("amount");
                if (rawAmount == null) {
                    throw new IllegalArgumentException(
                            "Loan requests must include amount in extra fields"
                    );
                }
                loanMatrixService.validateAmount(
                        parseAmount(rawAmount),
                        Boolean.TRUE.equals(dto.getExtraFields().get("is_top_up"))
                );
            }
            request.setExtraFields(dto.getExtraFields());
        }

        Request saved = requestRepository.save(request);

        auditService.log(
                currentUser,
                ActionType.REQUEST_UPDATED,
                "REQUEST",
                saved.getId().toString(),
                "REQUESTS",
                httpRequest
        );

        return toDTO(saved);
    }

    // ─── Delete (Draft only) ──────────────────────────────────

    @Transactional
    public void deleteDraft(
            UUID id,
            User currentUser,
            HttpServletRequest httpRequest) {

        Request request = findRequestById(id);

        // Only the initiator can discard their own draft
        if (!request.getInitiator().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException(
                    "You can only delete your own requests"
            );
        }

        // Anything past DRAFT is part of an audit trail — cancel it instead
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Only draft requests can be deleted. Cancel the request instead."
            );
        }

        // Log before the row disappears so the audit trail keeps the case id
        auditService.log(
                currentUser,
                ActionType.REQUEST_DELETED,
                "REQUEST",
                request.getId().toString(),
                "REQUESTS",
                httpRequest
        );

        // Attachments cascade via CascadeType.ALL; a draft has no stages yet
        requestRepository.delete(request);
    }

    // ─── Helpers ──────────────────────────────────────────────

    private Request findRequestForUpdate(UUID id) {
        return requestRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Request not found"));
    }

    private Request findRequestById(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Request not found"));
    }

//    private ApprovalStage findActiveStageForUser(
//            Request request,
//            User user) {
//
//        Integer currentIndex = approvalStageRepository
//                .findCurrentStageIndex(request)
//                .orElseThrow(() ->
//                        new IllegalArgumentException(
//                                "No active stage found for this request"
//                        ));
//
//        return approvalStageRepository
//                .findByRequestAndStageIndex(request, currentIndex)
//                .stream()
//                .filter(s -> s.getAssignedRole() == user.getRole()
//                        && s.getStatus() == StageStatus.PENDING)
//                .findFirst()
//                .orElseThrow(() ->
//                        new IllegalArgumentException(
//                                "You are not authorized to act on this request"
//                        ));
//    }

    private List<ApprovalStage> findActionableStages(
            Request request,
            User user) {

        Integer currentIndex = approvalStageRepository
                .findCurrentStageIndex(request)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No active stage found for this request"
                        ));

        List<ApprovalStage> open = approvalStageRepository
                .findByRequestAndStageIndex(request, currentIndex)
                .stream()
                .filter(s -> s.getStatus() == StageStatus.PENDING)
                .collect(Collectors.toList());

        // Admins hold every right: they resolve the whole current step.
        if (user.getRole() == Role.ADMIN && !open.isEmpty()) {
            return open;
        }

        return open.stream()
                .filter(s -> canActOnStage(s, request, user))
                .findFirst()
                .map(List::of)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "You are not authorized to act on this request"
                        ));
    }

    private void validateCanInitiate(User user) {
        if (!user.canInitiateRequests()) {
            throw new IllegalArgumentException(
                    "Your role is not allowed to create requests"
            );
        }
    }

    private void validateExtraFields(CreateRequestDto dto) {
        if (dto.getType() == RequestType.LOAN) {
            if (dto.getExtraFields() == null ||
                    dto.getExtraFields().get("amount") == null) {
                throw new IllegalArgumentException(
                        "Loan requests must include amount in extra fields"
                );
            }

            // Enforce the loan matrix bounds up front. This used to run only
            // at submit time, so an out-of-range amount still persisted a
            // draft that the UI could never surface or delete.
            BigDecimal amount = parseAmount(
                    dto.getExtraFields().get("amount")
            );
            boolean isTopUp = Boolean.TRUE.equals(
                    dto.getExtraFields().get("is_top_up")
            );
            loanMatrixService.validateAmount(amount, isTopUp);
        }
        if (dto.getType() == RequestType.VEHICLE) {
            if (dto.getExtraFields() == null ||
                    dto.getExtraFields().get("destination") == null) {
                throw new IllegalArgumentException(
                        "Vehicle requests must include destination in extra fields"
                );
            }
        }
        if (dto.getType() == RequestType.EQUIPMENT) {
            if (dto.getExtraFields() == null ||
                    dto.getExtraFields().get("item_name") == null) {
                throw new IllegalArgumentException(
                        "Equipment requests must include item_name in extra fields"
                );
            }
        }
    }

    // extraFields is free-form JSON, so amount can arrive as Integer,
    // Double or String depending on the client.
    private BigDecimal parseAmount(Object raw) {
        try {
            return new BigDecimal(raw.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Loan amount must be a valid number"
            );
        }
    }

    private void validateTopUp(Request parent, CreateRequestDto dto) {
        if (parent.getType() != RequestType.LOAN) {
            throw new IllegalArgumentException(
                    "Top-up can only be linked to a loan request"
            );
        }
        // MSME Gold does not support top-ups
        String loanType = (String) parent.getExtraField("loan_type");
        if ("MSME_GOLD".equals(loanType)) {
            throw new IllegalArgumentException(
                    "MSME Gold loans do not support top-ups"
            );
        }
    }

    // Auto-generate reference number CN-001, CN-002...
    private String generateReferenceNumber() {
        requestRepository.lockCaseIdGeneration();
        return requestRepository.findLatestCaseId()
                .map(last -> {
                    int number = Integer.parseInt(last.replace("CN-", ""));
                    return String.format("CN-%03d", number + 1);
                })
                .orElse("CN-001");
    }

    // ─── DTO Mapper ───────────────────────────────────────────

    public RequestResponseDto toDTO(Request request) {
        return RequestResponseDto.builder()
                .id(request.getId())
                .caseId(request.getCaseId())
                .type(request.getType())
                .status(request.getStatus())
                .title(request.getTitle())
                .details(request.getDetails())
                .notes(request.getNotes())
                .initiatorId(request.getInitiator().getId())
                .initiatorName(request.getInitiator().getFullName())
                .departmentId(request.getDepartment().getId())
                .departmentName(request.getDepartment().getName())
                .extraFields(request.getExtraFields())
                .approvalStages(
                        request.getApprovalStages().stream()
                                .map(this::toStageDTO)
                                .collect(Collectors.toList())
                )
                .attachments(
                        request.getAttachments().stream()
                                .map(this::toAttachmentDTO)
                                .collect(Collectors.toList())
                )
                .rejectionReason(request.getRejectionReason())
                .rejectedByName(
                        request.getRejectedBy() != null
                                ? request.getRejectedBy().getFullName()
                                : null
                )
                .rejectedAt(request.getRejectedAt())
                .parentReferenceNumber(
                        request.getParentRequest() != null
                                ? request.getParentRequest().getCaseId()
                                : null
                )
                .dueDate(request.getDueDate())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .isOverdue(request.isOverdue())
                .build();
    }

    private RequestResponseDto.ApprovalStageDto toStageDTO(
            ApprovalStage stage) {
        return RequestResponseDto.ApprovalStageDto.builder()
                .id(stage.getId())
                .stageIndex(stage.getStageIndex())
                .assignedRole(stage.getAssignedRole().name())
                .actedByName(
                        stage.getActedBy() != null
                                ? stage.getActedBy().getFullName()
                                : null
                )
                .status(stage.getStatus().name())
                .comment(stage.getComment())
                .isParallel(stage.isParallel())
                .assignedAt(stage.getAssignedAt())
                .actedAt(stage.getActedAt())
                .durationMinutes(stage.getDurationMinutes())
                .build();
    }

    private RequestResponseDto.AttachmentDto toAttachmentDTO(
            RequestAttachment attachment) {
        return RequestResponseDto.AttachmentDto.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .uploadedAt(attachment.getUploadedAt())
                .uploadedByName(attachment.getUploadedBy().getFullName())
                .build();
    }


    private void validateRoleForRequestType(Role role, RequestType type) {
        switch (type) {
            case LOAN -> {
                if (role != Role.LOAN_OFFICER) {
                    throw new IllegalArgumentException(
                            "Only Loan Officers can create Loan requests"
                    );
                }
            }
            case EQUIPMENT -> {
                if (role == Role.DRIVER || role == Role.AUDITOR) {
                    throw new IllegalArgumentException(
                            "Your role is not permitted to create Equipment requests"
                    );
                }
            }
            case VEHICLE -> {
                if (role == Role.AUDITOR) {
                    throw new IllegalArgumentException(
                            "Your role is not permitted to create Vehicle requests"
                    );
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unknown request type: " + type
            );
        }
    }

}