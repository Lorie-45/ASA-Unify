package com.asa.asaunify.services;




import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.RequestRepo;
import com.asa.asaunify.repos.UserRepo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserReportService {

    private final UserRepo userRepository;
    private final RequestRepo requestRepository;
    private final ApprovalStageRepo approvalStageRepository;

    // ─── User Activity Report ─────────────────────────────────

    public List<UserActivityDTO> getUserActivityReport(
            LocalDateTime from,
            LocalDateTime to) {

        return userRepository.findAll()
                .stream()
                .filter(User::isActive)
                .map(user -> buildActivityDTO(user, from, to))
                .collect(Collectors.toList());
    }

    // Activity for a single user
    public UserActivityDTO getUserActivity(
            UUID userId,
            LocalDateTime from,
            LocalDateTime to) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + userId));

        return buildActivityDTO(user, from, to);
    }

    // ─── Task Assignment Report ───────────────────────────────

    public List<TaskAssignmentDTO> getTaskAssignmentReport(
            UUID userId,
            LocalDateTime from,
            LocalDateTime to) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        return approvalStageRepository
                .findByActedBy(user)
                .stream()
                .filter(s -> from == null ||
                        (s.getActedAt() != null &&
                                !s.getActedAt().isBefore(from)))
                .filter(s -> to == null ||
                        (s.getActedAt() != null &&
                                !s.getActedAt().isAfter(to)))
                .map(this::toTaskDTO)
                .collect(Collectors.toList());
    }

    // ─── Helpers ──────────────────────────────────────────────

    private UserActivityDTO buildActivityDTO(
            User user,
            LocalDateTime from,
            LocalDateTime to) {

        // Cases initiated by this user
        long casesInitiated = requestRepository
                .findByInitiatorOrderByCreatedAtDesc(user)
                .stream()
                .filter(r -> from == null ||
                        !r.getCreatedAt().isBefore(from))
                .filter(r -> to == null ||
                        !r.getCreatedAt().isAfter(to))
                .count();

        // Stages acted on by this user
        List<ApprovalStage> actedStages = approvalStageRepository
                .findByActedBy(user)
                .stream()
                .filter(s -> from == null ||
                        (s.getActedAt() != null &&
                                !s.getActedAt().isBefore(from)))
                .filter(s -> to == null ||
                        (s.getActedAt() != null &&
                                !s.getActedAt().isAfter(to)))
                .collect(Collectors.toList());

        long tasksCompleted = actedStages.size();

        // Average time to act on a stage in minutes
        double avgTaskMinutes = actedStages.stream()
                .filter(s -> s.getDurationMinutes() != null)
                .mapToLong(ApprovalStage::getDurationMinutes)
                .average()
                .orElse(0.0);

        return UserActivityDTO.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .departmentName(
                        user.getDepartment() != null
                                ? user.getDepartment().getName()
                                : null
                )
                .casesInitiated(casesInitiated)
                .tasksCompleted(tasksCompleted)
                .averageTaskMinutes(
                        Math.round(avgTaskMinutes * 10.0) / 10.0
                )
                .build();
    }

    private TaskAssignmentDTO toTaskDTO(ApprovalStage stage) {
        return TaskAssignmentDTO.builder()
                .stageId(stage.getId())
                .requestReferenceNumber(
                        stage.getRequest().getCaseId()
                )
                .requestTitle(stage.getRequest().getTitle())
                .assignedRole(stage.getAssignedRole().name())
                .status(stage.getStatus().name())
                .assignedAt(stage.getAssignedAt())
                .actedAt(stage.getActedAt())
                .durationMinutes(stage.getDurationMinutes())
                .comment(stage.getComment())
                .build();
    }

    // ─── DTOs ─────────────────────────────────────────────────

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class UserActivityDTO {
        private UUID userId;
        private String fullName;
        private String email;
        private String role;
        private String departmentName;
        private long casesInitiated;
        private long tasksCompleted;
        private double averageTaskMinutes;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class TaskAssignmentDTO {
        private UUID stageId;
        private String requestReferenceNumber;
        private String requestTitle;
        private String assignedRole;
        private String status;
        private LocalDateTime assignedAt;
        private LocalDateTime actedAt;
        private Long durationMinutes;
        private String comment;
    }
}