package com.asa.asaunify.services;

import com.asa.asaunify.dtos.NotificationDto;
import com.asa.asaunify.dtos.NotificationSummaryDto;
import com.asa.asaunify.entity.*;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.NotificationRepo;
import com.asa.asaunify.repos.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
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

    // ─── Request Notifications ────────────────────────────────

    // Called when a request is submitted
    // Notifies all users with the role assigned to stage 1
    @Async
    public void notifyRequestSubmitted(Request request) {
        // Find stage 1 assignee role
        request.getApprovalStages().stream()
                .filter(s -> s.getStageIndex() == 1)
                .findFirst()
                .ifPresent(stage -> {
                    String title = "New Request Assigned";
                    String body = String.format(
                            "Request %s — %s has been submitted and requires your action.",
                            request.getCaseId(),
                            request.getTitle()
                    );
                    notifyByRole(
                            stage.getAssignedRole(),
                            title, body,
                            ActionType.REQUEST_SUBMITTED,
                            request, null
                    );
                });
    }

    // Called when a sequential stage becomes active
    // Notifies all users with the role at that stage index
    @Async
    public void notifyStageActivated(Request request, int stageIndex) {
        request.getApprovalStages().stream()
                .filter(s -> s.getStageIndex() == stageIndex)
                .findFirst()
                .ifPresent(stage -> {
                    String title = "Action Required";
                    String body = String.format(
                            "Request %s — %s has reached your stage and requires your review.",
                            request.getCaseId(),
                            request.getTitle()
                    );
                    notifyByRole(
                            stage.getAssignedRole(),
                            title, body,
                            ActionType.STAGE_APPROVED,
                            request, null
                    );
                });
    }

    // Called when a request is fully completed
    // Notifies the initiator
    @Async
    public void notifyCompleted(Request request) {
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
    }

    // Called when a request is fully approved (all parallel stages approved)
// Notifies the initiator
    @Async
    public void notifyApproved(Request request) {
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
    }

    // Called when any stage is rejected
    // Notifies initiator + all users who previously acted on the request
    @Async
    public void notifyRejection(Request request, ApprovalStage rejectedStage) {
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
        request.getApprovalStages().stream()
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
    }

    // ─── Memo Notifications ───────────────────────────────────

    // Called when a memo is submitted
    // Notifies all selected approver roles
    @Async
    public void notifyMemoSubmitted(Memo memo) {
        String title = "Memo Requires Your Approval";
        String body = String.format(
                "Memo %s — %s has been submitted and requires your review.",
                memo.getReferenceNumber(),
                memo.getTitle()
        );

        memo.getApprovalStages().forEach(stage ->
                notifyByRole(
                        stage.getAssignedRole(),
                        title, body,
                        ActionType.MEMO_CREATED,
                        null, memo
                )
        );
    }

    // Called when a memo stage is approved or rejected
    // Notifies the author + all other approvers
    @Async
    public void notifyMemoStageAction(Memo memo, MemoApprovalStage stage) {
        String actionWord = stage.getStatus() == StageStatus.APPROVED
                ? "approved" : "rejected";

        String title = String.format("Memo %s", actionWord);
        String body = String.format(
                "Memo %s — %s was %s by %s.",
                memo.getReferenceNumber(),
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
                .forEach(s -> notifyByRole(
                        s.getAssignedRole(),
                        title, body,
                        ActionType.MEMO_APPROVED,
                        null, memo
                ));
    }

    // Called when memo is fully approved or rejected
    // Notifies the author
    @Async
    public void notifyMemoCompleted(Memo memo) {
        String isApproved = memo.getStatus().name().equals("APPROVED")
                ? "approved" : "rejected";

        String title = String.format("Memo %s", isApproved);
        String body = String.format(
                "Your memo %s — %s has been %s.",
                memo.getReferenceNumber(),
                memo.getTitle(),
                isApproved
        );

        sendAndPush(
                memo.getAuthor(),
                title, body,
                ActionType.MEMO_APPROVED,
                null, memo
        );
    }

    // ─── Driver Notification ──────────────────────────────────

    @Async
    public void notifyDriverAssigned(Request request, User driver) {
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
    }

    // ─── User-facing queries ──────────────────────────────────

    // Returns unread count + last 10 notifications
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
    public void markAsRead(UUID notificationId) {
        notificationRepository.markAsRead(notificationId);
    }

    // ─── Core send helpers ────────────────────────────────────

    // Saves notification to DB and pushes via WebSocket
    private void sendAndPush(
            User user,
            String title,
            String body,
            ActionType actionType,
            Request request,
            Memo memo) {

        try {
            Notification notification = Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .actionType(actionType)
                    .request(request)
                    .memo(memo)
                    .isRead(false)
                    .build();

            Notification saved = notificationRepository.save(notification);

            // Push via WebSocket to user's private channel
            // Frontend listens on /user/{userId}/queue/notifications
            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/queue/notifications",
                    toDTO(saved)
            );

        } catch (Exception e) {
            log.error(
                    "Failed to send notification to user {}: {}",
                    user.getId(), e.getMessage()
            );
        }
    }

    // Sends notification to all active users with a specific role
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
                                ? notification.getMemo().getReferenceNumber()
                                : null
                )
                .build();
    }
}