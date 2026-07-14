package com.asa.asaunify.services;



import com.asa.asaunify.dtos.*;
import com.asa.asaunify.entity.*;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.*;
import com.asa.asaunify.workflow.WorkflowEngine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent request not found"
                    ));
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

        Request request = findRequestById(requestId);

        log.info("=== processAction ===");
        log.info("User: {} Role: {}", currentUser.getEmail(), currentUser.getRole());
        log.info("Request ID: {}", request.getId());

        // Validate comment is provided on rejection
        if (dto.getAction() == StageStatus.REJECTED &&
                (dto.getComment() == null || dto.getComment().isBlank())) {
            throw new IllegalArgumentException(
                    "A comment is required when rejecting a request"
            );
        }

        // Find the stage assigned to the current user's role
        // For parallel stages, allow action even if request is REJECTED
        // (another parallel approver may have already rejected)
        ApprovalStage stage = findActiveStageForUser(request, currentUser);

        // Validate the request can still be acted on
        // Sequential: must be PENDING
        // Parallel: allow action if stage is still PENDING regardless of request status
        if (!stage.isParallel() && request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Request is not in a pending state"
            );
        }

        // Completed requests can never be acted on
        if (request.getStatus() == RequestStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "Request has already been completed"
            );
        }

        // Check if logistics action
        if (currentUser.getRole() == Role.LOGISTICS &&
                stage.getStageIndex() == 2 &&
                dto.getInStock() != null) {

            workflowEngine.processLogisticsAction(
                    request, stage, dto.getInStock(), dto.getComment()
            );

        } else {
            // Single call to workflow engine — removed the duplicate
            workflowEngine.processAction(
                    request, stage, dto.getAction(), dto.getComment()
            );
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

    // ─── Cancel (Admin / Dept Head / Higher-ups) ──────────────

    @Transactional
    public RequestResponseDto cancelRequest(
            UUID requestId,
            String reason,
            User currentUser,
            HttpServletRequest httpRequest) {

        Request request = findRequestById(requestId);

        validateCanCancel(currentUser);

        if (request.getStatus() == RequestStatus.COMPLETED ||
                request.getStatus() == RequestStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Cannot cancel a completed or already rejected request"
            );
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectedBy(currentUser);
        request.setRejectedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        requestRepository.save(request);

        // Notify all involved parties
        notificationService.notifyRejection(request, null);

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

    // ─── Assign Driver (Fleet Manager only) ───────────────────

    @Transactional
    public void assignDriver(
            UUID requestId,
            AssignDriverDto dto,
            User currentUser,
            HttpServletRequest httpRequest) {

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

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
                .orElseThrow(() -> new IllegalArgumentException("Driver not found"));

        if (driver.getRole() != Role.DRIVER) {
            throw new IllegalArgumentException("Selected user is not a driver");
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
        try {
            log.info("=== getPendingForRole START ===");
            log.info("User: {} Role: {}", user.getEmail(), user.getRole());

            List<ApprovalStage> stages = approvalStageRepository
                    .findByAssignedRoleAndStatus(user.getRole(), StageStatus.PENDING);

            log.info("Found {} stages", stages.size());

            List<RequestResponseDto> result = stages.stream()
                    .map(ApprovalStage::getRequest)
                    .distinct() // avoid duplicates from multiple stages
                    .filter(r ->
                            // Sequential stages — only show if request is still PENDING
                            // Parallel stages — show even if request was prematurely
                            // marked REJECTED, as long as it's not COMPLETED
                            r.getStatus() == RequestStatus.PENDING ||
                                    (r.getStatus() == RequestStatus.REJECTED &&
                                            stages.stream()
                                                    .anyMatch(s -> s.getRequest().getId().equals(r.getId())
                                                            && s.isParallel()))
                    )
                    .filter(r -> r.getStatus() != RequestStatus.COMPLETED)
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            log.info("Returning {} results", result.size());
            return result;

        } catch (Exception e) {
            log.error("=== getPendingForRole FAILED ===", e);
            return List.of();
        }
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
    public RequestResponseDto getRequestById(UUID id) {
        return toDTO(findRequestById(id));
    }

    @Transactional
    public RequestResponseDto updateDraft(
            UUID id,
            UpdateRequestDto dto,
            User currentUser,
            HttpServletRequest httpRequest) {

        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

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
        if (dto.getExtraFields() != null) request.setExtraFields(dto.getExtraFields());

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

    // ─── Helpers ──────────────────────────────────────────────

    private Request findRequestById(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Request not found: " + id));
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

    private ApprovalStage findActiveStageForUser(
            Request request,
            User user) {

        Integer currentIndex = approvalStageRepository
                .findCurrentStageIndex(request)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No active stage found for this request"
                        ));

        log.info("Current stage index: {}", currentIndex);
        log.info("Looking for role: {}", user.getRole());

        // List all stages at this index for debugging
        List<ApprovalStage> stages = approvalStageRepository
                .findByRequestAndStageIndex(request, currentIndex);

        stages.forEach(s -> log.info(
                "Stage: assignedRole={} status={} roleMatch={} statusMatch={}",
                s.getAssignedRole(),
                s.getStatus(),
                s.getAssignedRole().equals(user.getRole()),  // use equals not ==
                s.getStatus() == StageStatus.PENDING
        ));

        return stages.stream()
                .filter(s -> s.getAssignedRole().equals(user.getRole())  // ← .equals() not ==
                        && s.getStatus() == StageStatus.PENDING)
                .findFirst()
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

    private void validateCanCancel(User user) {
        boolean canCancel = user.getRole() == Role.ADMIN
                || user.getRole() == Role.DEPARTMENT_HEAD
                || user.getRole() == Role.RM
                || user.getRole() == Role.MSME_OFFICER
                || user.getRole() == Role.CREDIT_OFFICER;

        if (!canCancel) {
            throw new IllegalArgumentException(
                    "You are not authorized to cancel requests"
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
        return requestRepository.findLatestReferenceNumber()
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
                if (role != Role.LOAN_OFFICER &&
                        role != Role.MSME_OFFICER &&
                        role != Role.RM) {
                    throw new IllegalArgumentException(
                            "Only Loan Officers, MSME Officers, and RMs can create Loan requests"
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