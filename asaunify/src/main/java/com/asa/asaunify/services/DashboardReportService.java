package com.asa.asaunify.services;


import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.enums.StageStatus;
import com.asa.asaunify.repos.ApprovalStageRepo;
import com.asa.asaunify.repos.RequestRepo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardReportService {

    private final RequestRepo requestRepository;
    private final ApprovalStageRepo approvalStageRepository;

    // ─── Main dashboard KPI summary ───────────────────────────

    @Transactional(readOnly = true)
    public DashboardSummaryDTO getSummary(
            User currentUser,
            LocalDateTime from,
            LocalDateTime to) {

        // A department head sees only their own department's numbers.
        if (currentUser.getRole() == Role.DEPARTMENT_HEAD) {
            if (currentUser.getDepartment() == null) {
                throw new IllegalArgumentException(
                        "User does not belong to a department");
            }
            return getDepartmentSummary(currentUser, from, to);
        }

        // Total counts per status
        long totalPending = requestRepository
                .countByStatus(RequestStatus.PENDING);
        long totalApproved = requestRepository
                .countByStatus(RequestStatus.APPROVED);
        long totalCompleted = requestRepository
                .countByStatus(RequestStatus.COMPLETED);
        long totalRejected = requestRepository
                .countByStatus(RequestStatus.REJECTED);

        long total = totalPending + totalApproved +
                totalCompleted + totalRejected;

        // On-time rate
        long completedOnTime = requestRepository
                .countCompletedOnTime(
                        from != null ? from : LocalDateTime.now().minusMonths(1),
                        to != null ? to : LocalDateTime.now()
                );
        long completedInPeriod = requestRepository
                .countCompletedInPeriod(
                        from != null ? from : LocalDateTime.now().minusMonths(1),
                        to != null ? to : LocalDateTime.now()
                );

        double onTimeRate = completedInPeriod > 0
                ? (double) completedOnTime / completedInPeriod * 100
                : 0.0;

        // Counts per request type
        Map<String, Long> byType = new LinkedHashMap<>();
        byType.put("EQUIPMENT", requestRepository
                .countByTypeAndStatus(RequestType.EQUIPMENT,
                        RequestStatus.PENDING));
        byType.put("VEHICLE", requestRepository
                .countByTypeAndStatus(RequestType.VEHICLE,
                        RequestStatus.PENDING));
        byType.put("LOAN", requestRepository
                .countByTypeAndStatus(RequestType.LOAN,
                        RequestStatus.PENDING));

        // Bottleneck detection — which role takes longest on average
        Map<String, Double> avgTimeByRole = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            if (role == Role.DRIVER || role == Role.AUDITOR) continue;

            List<ApprovalStage> completedStages = approvalStageRepository
                    .findCompletedStagesByRole(role, StageStatus.PENDING);

            double avg = completedStages.stream()
                    .filter(s -> s.getDurationMinutes() != null)
                    .mapToLong(ApprovalStage::getDurationMinutes)
                    .average()
                    .orElse(0.0);

            if (avg > 0) {
                avgTimeByRole.put(role.name(),
                        Math.round(avg * 10.0) / 10.0);
            }
        }

        return DashboardSummaryDTO.builder()
                .totalRequests(total)
                .totalPending(totalPending)
                .totalApproved(totalApproved)
                .totalCompleted(totalCompleted)
                .totalRejected(totalRejected)
                .onTimeRatePercent(Math.round(onTimeRate * 10.0) / 10.0)
                .pendingByType(byType)
                .averageCompletionMinutesByRole(avgTimeByRole)
                .build();
    }

    // Department-scoped version of the summary. Departments are small, so the
    // figures are derived from the department's own requests and stages.
    private DashboardSummaryDTO getDepartmentSummary(
            User head, LocalDateTime from, LocalDateTime to) {

        List<Request> requests = requestRepository
                .findByDepartmentOrderByCreatedAtDesc(head.getDepartment());

        long totalPending = countStatus(requests, RequestStatus.PENDING);
        long totalApproved = countStatus(requests, RequestStatus.APPROVED);
        long totalCompleted = countStatus(requests, RequestStatus.COMPLETED);
        long totalRejected = countStatus(requests, RequestStatus.REJECTED);

        LocalDateTime start = from != null ? from : LocalDateTime.now().minusMonths(1);
        LocalDateTime end = to != null ? to : LocalDateTime.now();

        List<Request> completedInPeriod = requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.COMPLETED)
                .filter(r -> !r.getCreatedAt().isBefore(start)
                        && !r.getCreatedAt().isAfter(end))
                .toList();
        long onTime = completedInPeriod.stream()
                .filter(r -> r.getDueDate() != null
                        && !r.getUpdatedAt().isAfter(r.getDueDate()))
                .count();
        double onTimeRate = completedInPeriod.isEmpty()
                ? 0.0
                : (double) onTime / completedInPeriod.size() * 100;

        Map<String, Long> byType = new LinkedHashMap<>();
        for (RequestType type : new RequestType[]{
                RequestType.EQUIPMENT, RequestType.VEHICLE, RequestType.LOAN}) {
            byType.put(type.name(), requests.stream()
                    .filter(r -> r.getType() == type
                            && r.getStatus() == RequestStatus.PENDING)
                    .count());
        }

        Map<String, Double> avgTimeByRole = new LinkedHashMap<>();
        Map<Role, List<ApprovalStage>> stagesByRole = requests.stream()
                .flatMap(r -> r.getApprovalStages().stream())
                .filter(s -> s.getActedAt() != null
                        && s.getStatus() != StageStatus.PENDING
                        && s.getDurationMinutes() != null)
                .collect(java.util.stream.Collectors
                        .groupingBy(ApprovalStage::getAssignedRole));
        for (Role role : Role.values()) {
            if (role == Role.DRIVER || role == Role.AUDITOR) continue;
            double avg = stagesByRole.getOrDefault(role, List.of()).stream()
                    .mapToLong(ApprovalStage::getDurationMinutes)
                    .average().orElse(0.0);
            if (avg > 0) {
                avgTimeByRole.put(role.name(), Math.round(avg * 10.0) / 10.0);
            }
        }

        return DashboardSummaryDTO.builder()
                .totalRequests(totalPending + totalApproved
                        + totalCompleted + totalRejected)
                .totalPending(totalPending)
                .totalApproved(totalApproved)
                .totalCompleted(totalCompleted)
                .totalRejected(totalRejected)
                .onTimeRatePercent(Math.round(onTimeRate * 10.0) / 10.0)
                .pendingByType(byType)
                .averageCompletionMinutesByRole(avgTimeByRole)
                .build();
    }

    private long countStatus(List<Request> requests, RequestStatus status) {
        return requests.stream().filter(r -> r.getStatus() == status).count();
    }

    // ─── DTO ──────────────────────────────────────────────────

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class DashboardSummaryDTO {

        private long totalRequests;
        private long totalPending;
        private long totalApproved;
        private long totalCompleted;
        private long totalRejected;

        // Percentage of requests completed before their due date
        private double onTimeRatePercent;

        // How many pending requests per type
        private Map<String, Long> pendingByType;

        // Average minutes each role takes to act on a stage
        // Used to detect bottlenecks in the approval chain
        private Map<String, Double> averageCompletionMinutesByRole;
    }
}