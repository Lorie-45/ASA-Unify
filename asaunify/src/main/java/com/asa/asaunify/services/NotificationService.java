package com.asa.asaunify.services;

import com.asa.asaunify.dtos.NotificationDto;
import com.asa.asaunify.dtos.NotificationSummaryDto;
import com.asa.asaunify.entity.*;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.exceptions.ResourceNotFoundException;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.MemoRepo;
import com.asa.asaunify.repos.NotificationRepo;
import com.asa.asaunify.repos.RequestRepo;
import com.asa.asaunify.repos.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepo notificationRepository;
    private final UserRepo userRepository;
    private final ApprovalStageRepo approvalStageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RequestRepo requestRepository;
    private final MemoRepo memoRepository;
    private final PlatformTransactionManager transactionManager;

    // ─── Request Notifications ────────────────────────────────

    // Called when a request is submitted
    // Notifies all users with the role assigned to stage 1
    public void notifyRequestSubmitted(Request request) {
        try {
        // Find stage 1 assignee role
        // Every stage of step 1: loans open three parallel stages at once.
        approvalStageRepository.findByRequestOrderByStageIndexAsc(request).stream()
                .filter(s -> s.getStageIndex() == 1)
                .forEach(stage -> {
                    String title = "New Request Assigned";
                    String body = String.format(
                            "Request %s — %s has been submitted and requires your action.",
                            request.getCaseId(),
                            request.getTitle()
                    );
                    notifyStageApprovers(
                            request, stage,
                            title, body,
                            ActionType.REQUEST_SUBMITTED
                    );
                });
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyRequestSubmitted", e.getMessage(), e);
        }
    }

    // Called when a sequential stage becomes active
    // Notifies all users with the role at that stage index
    public void notifyStageActivated(Request request, int stageIndex) {
        try {
        approvalStageRepository.findByRequestOrderByStageIndexAsc(request).stream()
                .filter(s -> s.getStageIndex() == stageIndex)
                .forEach(stage -> {
                    String title = "Action Required";
                    String body = String.format(
                            "Request %s — %s has reached your stage and requires your review.",
                            request.getCaseId(),
                            request.getTitle()
                    );
                    notifyStageApprovers(
                            request, stage,
                            title, body,
                            ActionType.STAGE_APPROVED
                    );
                });
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyStageActivated", e.getMessage(), e);
        }
    }

    // Called when a request is fully completed
    // Notifies the initiator
    public void notifyCompleted(Request request) {
        try {
        String title = "Request Completed";
        String body = String.format(
                "Your request %s — %s has been completed successfully.",
                request.getCaseId(),
                request.getTitle()
        );
        sendAndPush(
                request.getInitiator(),
                title, body,
                ActionType.REQUEST_COMPLETED,
                request, null
        );
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyCompleted", e.getMessage(), e);
        }
    }

    // Called when a request is fully approved (all parallel stages approved)
// Notifies the initiator
    public void notifyApproved(Request request) {
        try {
        String title = "Request Approved";
        String body = String.format(
                "Your request %s — %s has been approved by all reviewers.",
                request.getCaseId(),
                request.getTitle()
        );
        sendAndPush(
                request.getInitiator(),
                title, body,
                ActionType.REQUEST_APPROVED,
                request, null
        );
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyApproved", e.getMessage(), e);
        }
    }

    // Called when any stage is rejected
    // Notifies initiator + all users who previously acted on the request
    public void notifyRejection(Request request, ApprovalStage rejectedStage) {
        try {
        String comment = rejectedStage != null
                ? rejectedStage.getComment()
                : "No reason provided";

        String title = "Request Rejected";
        String body = String.format(
                "Request %s — %s has been rejected. Reason: %s",
                request.getCaseId(),
                request.getTitle(),
                comment
        );

        // Collect all users who need to be notified
        List<User> toNotify = new ArrayList<>();

        // Always notify the initiator
        toNotify.add(request.getInitiator());

        // Notify all users who previously acted on any stage
        approvalStageRepository.findByRequestOrderByStageIndexAsc(request).stream()
                .filter(s -> s.getActedBy() != null)
                .map(ApprovalStage::getActedBy)
                .filter(u -> !u.getId().equals(request.getInitiator().getId()))
                .forEach(toNotify::add);

        // Send to each unique user
        toNotify.stream()
                .distinct()
                .forEach(user -> sendAndPush(
                        user, title, body,
                        ActionType.REQUEST_REJECTED,
                        request, null
                ));
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyRejection", e.getMessage(), e);
        }
    }

    // ─── Memo Notifications ───────────────────────────────────

    // Called when a memo is submitted
    // Notifies all selected approver roles
    public void notifyMemoSubmitted(Memo memo) {
        try {
        String title = "Memo Requires Your Approval";
        String body = String.format(
                "Memo %s — %s has been submitted and requires your review.",
                memo.getCaseId(),
                memo.getTitle()
        );

        memo.getApprovalStages().forEach(stage ->
                notifyMemoApprover(
                        stage, title, body,
                        ActionType.MEMO_CREATED, memo
                )
        );
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyMemoSubmitted", e.getMessage(), e);
        }
    }

    // Called when a memo stage is approved or rejected
    // Notifies the author + all other approvers
    public void notifyMemoStageAction(Memo memo, MemoApprovalStage stage) {
        try {
        String actionWord = stage.getStatus() == StageStatus.APPROVED
                ? "approved" : "rejected";

        String title = String.format("Memo %s", actionWord);
        String body = String.format(
                "Memo %s — %s was %s by %s.",
                memo.getCaseId(),
                memo.getTitle(),
                actionWord,
                stage.getActedBy() != null
                        ? stage.getActedBy().getFullName()
                        : "Unknown"
        );

        // Notify the author
        sendAndPush(
                memo.getAuthor(),
                title, body,
                stage.getStatus() == StageStatus.APPROVED
                        ? ActionType.MEMO_APPROVED
                        : ActionType.MEMO_REJECTED,
                null, memo
        );

        // Notify all other approvers who haven't acted yet
        memo.getApprovalStages().stream()
                .filter(s -> s.getStatus() == StageStatus.PENDING)
                .filter(s -> !s.getId().equals(stage.getId()))
                .forEach(s -> notifyMemoApprover(
                        s, title, body,
                        ActionType.MEMO_APPROVED, memo
                ));
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyMemoStageAction", e.getMessage(), e);
        }
    }

    // Called when memo is fully approved or rejected
    // Notifies the author
    public void notifyMemoCompleted(Memo memo) {
        try {
        String isApproved = memo.getStatus().name().equals("APPROVED")
                ? "approved" : "rejected";

        String title = String.format("Memo %s", isApproved);
        String body = String.format(
                "Your memo %s — %s has been %s.",
                memo.getCaseId(),
                memo.getTitle(),
                isApproved
        );

        sendAndPush(
                memo.getAuthor(),
                title, body,
                ActionType.MEMO_APPROVED,
                null, memo
        );
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyMemoCompleted", e.getMessage(), e);
        }
    }

    // ─── Driver Notification ──────────────────────────────────

    public void notifyDriverAssigned(Request request, User driver) {
        try {
        String title = "Trip Assignment";
        String body = String.format(
                "You have been assigned to trip %s — %s. Please review the details.",
                request.getCaseId(),
                request.getTitle()
        );
        sendAndPush(
                driver, title, body,
                ActionType.DRIVER_ASSIGNED,
                request, null
        );
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyDriverAssigned", e.getMessage(), e);
        }
    }

    // ─── User-facing queries ──────────────────────────────────

    // Returns unread count + last 10 notifications
    @Transactional(readOnly = true)
    public NotificationSummaryDto getSummary(User user) {
        long unreadCount = notificationRepository
                .countByUserAndIsReadFalse(user);

        List<NotificationDto> recent = notificationRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .limit(10)
                .map(this::toDTO)
                .collect(Collectors.toList());

        return NotificationSummaryDto.builder()
                .unreadCount(unreadCount)
                .notifications(recent)
                .build();
    }

    // Returns all notifications for a user
    @Transactional(readOnly = true)
    public List<NotificationDto> getAllForUser(User user) {
        return notificationRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Mark all as read
    public void markAllAsRead(User user) {
        notificationRepository.markAllAsRead(user);
    }

    // Mark single notification as read
    public void markAsRead(UUID notificationId, User currentUser) {
        // Only the owner may mark their own notification read. Same response
        // as a genuine miss so IDs of other users cannot be probed.
        Notification notification = notificationRepository
                .findById(notificationId)
                .filter(n -> n.getUser() != null
                        && n.getUser().getId().equals(currentUser.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        notificationRepository.markAsRead(notification.getId());
    }

    // ─── Core send helpers ────────────────────────────────────

    // What gets delivered. Plain values only: by the time it is delivered the
    // caller's Hibernate session is closed, and a session is not thread-safe,
    // so no entity may travel to the delivery thread.
    private record Outgoing(
            UUID userId, String title, String body, ActionType actionType,
            UUID requestId, String requestCaseId,
            UUID memoId, String memoCaseId) {
    }

    // Single thread keeps notifications in the order they were raised.
    private final java.util.concurrent.Executor deliveryExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "notification-delivery");
                t.setDaemon(true);
                return t;
            });

    // Queues a notification. It is only delivered once the surrounding
    // transaction has COMMITTED: delivering earlier references rows the
    // database cannot see yet (foreign-key failure), and tells people about
    // something that may still roll back.
    private void sendAndPush(
            User user,
            String title,
            String body,
            ActionType actionType,
            Request request,
            Memo memo) {

        Outgoing out = new Outgoing(
                user.getId(), title, body, actionType,
                request != null ? request.getId() : null,
                request != null ? request.getCaseId() : null,
                memo != null ? memo.getId() : null,
                memo != null ? memo.getCaseId() : null);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deliveryExecutor.execute(() -> deliver(out));
                        }
                    });
        } else {
            deliveryExecutor.execute(() -> deliver(out));
        }
    }

    // Runs on the delivery thread, in its own transaction.
    private void deliver(Outgoing out) {
        try {
            Notification saved = new TransactionTemplate(transactionManager)
                    .execute(status -> notificationRepository.save(
                            Notification.builder()
                                    .user(userRepository.getReferenceById(out.userId()))
                                    .title(out.title())
                                    .body(out.body())
                                    .actionType(out.actionType())
                                    .request(out.requestId() != null
                                            ? requestRepository.getReferenceById(out.requestId())
                                            : null)
                                    .memo(out.memoId() != null
                                            ? memoRepository.getReferenceById(out.memoId())
                                            : null)
                                    .isRead(false)
                                    .build()));

            // Delivered to the user's own queue; the STOMP session principal
            // is named after the user id (see WebSocketAuthInterceptor).
            messagingTemplate.convertAndSendToUser(
                    out.userId().toString(),
                    "/queue/notifications",
                    NotificationDto.builder()
                            .id(saved.getId())
                            .title(out.title())
                            .body(out.body())
                            .actionType(out.actionType())
                            .isRead(false)
                            .createdAt(saved.getCreatedAt())
                            .requestId(out.requestId())
                            .requestReferenceNumber(out.requestCaseId())
                            .memoId(out.memoId())
                            .memoReferenceNumber(out.memoCaseId())
                            .build());

        } catch (Exception e) {
            log.error("Failed to send notification to user {}: {}",
                    out.userId(), e.getMessage());
        }
    }

    // Sends notification to all active users with a specific role
    // A department head's stage goes to the heads of the request's own
    // department only; every other stage goes to everyone holding that role.
    private void notifyStageApprovers(
            Request request,
            ApprovalStage stage,
            String title,
            String body,
            ActionType actionType) {

        if (stage.getAssignedRole() == Role.DEPARTMENT_HEAD
                && request.getDepartment() != null) {
            userRepository.findByDepartmentAndRoleAndIsActiveTrue(
                            request.getDepartment(), Role.DEPARTMENT_HEAD)
                    .forEach(user -> sendAndPush(
                            user, title, body, actionType, request, null));
            return;
        }
        notifyByRole(stage.getAssignedRole(), title, body,
                actionType, request, null);
    }

    // The author picked a specific person: tell that person, not everyone
    // who happens to share their role. (Legacy stages with no named person
    // still fall back to the role.)
    private void notifyMemoApprover(
            MemoApprovalStage stage,
            String title,
            String body,
            ActionType actionType,
            Memo memo) {

        if (stage.getAssignedTo() != null) {
            if (stage.getAssignedTo().isActive()) {
                sendAndPush(stage.getAssignedTo(), title, body,
                        actionType, null, memo);
            }
            return;
        }
        notifyByRole(stage.getAssignedRole(), title, body,
                actionType, null, memo);
    }

    // Called when an admin / department head cancels a request.
    public void notifyCancelled(Request request, String reason) {
        try {
        String title = "Request Cancelled";
        String body = String.format(
                "Request %s — %s has been cancelled. Reason: %s",
                request.getCaseId(),
                request.getTitle(),
                reason
        );

        List<User> toNotify = new ArrayList<>();
        toNotify.add(request.getInitiator());
        approvalStageRepository.findByRequestOrderByStageIndexAsc(request).stream()
                .map(ApprovalStage::getActedBy)
                .filter(u -> u != null)
                .forEach(toNotify::add);

        toNotify.stream()
                .filter(u -> !u.getId().equals(request.getRejectedBy().getId()))
                .distinct()
                .forEach(user -> sendAndPush(
                        user, title, body,
                        ActionType.REQUEST_CANCELLED,
                        request, null
                ));
        } catch (Exception e) {
            log.error("Could not prepare notifications ({}): {}",
                    "notifyCancelled", e.getMessage(), e);
        }
    }

    private void notifyByRole(
            Role role,
            String title,
            String body,
            ActionType actionType,
            Request request,
            Memo memo) {

        userRepository.findByRoleAndIsActiveTrue(role)
                .forEach(user -> sendAndPush(
                        user, title, body, actionType, request, memo
                ));
    }

    // ─── DTO Mapper ───────────────────────────────────────────

    public NotificationDto toDTO(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .actionType(notification.getActionType())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .requestId(
                        notification.getRequest() != null
                                ? notification.getRequest().getId()
                                : null
                )
                .requestReferenceNumber(
                        notification.getRequest() != null
                                ? notification.getRequest().getCaseId()
                                : null
                )
                .memoId(
                        notification.getMemo() != null
                                ? notification.getMemo().getId()
                                : null
                )
                .memoReferenceNumber(
                        notification.getMemo() != null
                                ? notification.getMemo().getCaseId()
                                : null
                )
                .build();
    }
}