package com.asa.asaunify.services;



import com.asa.asaunify.dtos.ApprovalActionDto;
import com.asa.asaunify.dtos.AssignDriverDto;
import com.asa.asaunify.dtos.CreateRequestDto;
import com.asa.asaunify.dtos.RequestResponseDto;
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
        validateExtraFields(dto);

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

        // Validate request is still pending
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Request is not in a pending state"
            );
        }

        // Validate comment is provided on rejection
        if (dto.getAction() == StageStatus.REJECTED &&
                (dto.getComment() == null || dto.getComment().isBlank())) {
            throw new IllegalArgumentException(
                    "A comment is required when rejecting a request"
            );
        }

        // Find the stage assigned to the current user's role
        ApprovalStage stage = findActiveStageForUser(request, currentUser);

        // Check if logistics action
        if (currentUser.getRole() == Role.LOGISTICS &&
                stage.getStageIndex() == 2 &&
                dto.getInStock() != null) {

            workflowEngine.processLogisticsAction(
                    request, stage, dto.getInStock(), dto.getComment()
            );

        } else {
            workflowEngine.processAction(
                    request, stage, dto.getAction(), dto.getComment()
            );
        }

        // If rejected — stamp rejection fields on the request
        if (dto.getAction() == StageStatus.REJECTED) {
            request.setRejectedBy(currentUser);
            request.setRejectedAt(LocalDateTime.now());
            request.setRejectionReason(dto.getComment());
            requestRepository.save(request);
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
            User fleetManager,
            HttpServletRequest httpRequest) {

        Request request = findRequestById(requestId);

        if (request.getType() != RequestType.VEHICLE) {
            throw new IllegalArgumentException(
                    "Driver assignment is only for vehicle requests"
            );
        }

        // Check no existing assignment
        if (tripAssignmentRepository.existsByRequest(request)) {
            throw new IllegalArgumentException(
                    "A driver has already been assigned to this request"
            );
        }

        User driver = userRepository.findById(dto.getDriverId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Driver not found"));

        if (driver.getRole() != Role.DRIVER) {
            throw new IllegalArgumentException(
                    "Assigned user is not a driver"
            );
        }

        VehicleTripAssignment assignment = VehicleTripAssignment.builder()
                .request(request)
                .driver(driver)
                .assignedBy(fleetManager)
                .build();

        tripAssignmentRepository.save(assignment);

        notificationService.notifyDriverAssigned(request, driver);

        auditService.log(
                fleetManager,
                ActionType.DRIVER_ASSIGNED,
                "REQUEST",
                request.getId().toString(),
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

    // ─── Queries ──────────────────────────────────────────────

    // My requests — initiator view
    public List<RequestResponseDto> getMyRequests(User user) {
        return requestRepository
                .findByInitiatorOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Department requests — department head view
    public List<RequestResponseDto> getDepartmentRequests(User deptHead) {
        return requestRepository
                .findByDepartmentOrderByCreatedAtDesc(deptHead.getDepartment())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Pending approvals — what is waiting on current user's role
    public List<RequestResponseDto> getPendingForRole(User user) {
        return approvalStageRepository
                .findByAssignedRoleAndStatus(user.getRole(), StageStatus.PENDING)
                .stream()
                .map(ApprovalStage::getRequest)
                .filter(r -> r.getStatus() == RequestStatus.PENDING)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // All requests — admin/auditor view
    public List<RequestResponseDto> getAllRequests() {
        return requestRepository
                .findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public RequestResponseDto getRequestById(UUID id) {
        return toDTO(findRequestById(id));
    }

    // ─── Helpers ──────────────────────────────────────────────

    private Request findRequestById(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Request not found: " + id));
    }

    private ApprovalStage findActiveStageForUser(
            Request request,
            User user) {

        Integer currentIndex = approvalStageRepository
                .findCurrentStageIndex(request)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No active stage found for this request"
                        ));

        return approvalStageRepository
                .findByRequestAndStageIndex(request, currentIndex)
                .stream()
                .filter(s -> s.getAssignedRole() == user.getRole()
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
}