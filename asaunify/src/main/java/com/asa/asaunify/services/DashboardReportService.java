package com.asa.asaunify.services;


import com.asa.asaunify.entity.ApprovalStage;
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