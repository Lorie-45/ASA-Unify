package com.asa.asaunify.services;



import com.asa.asaunify.entity.AuditLog;
import com.asa.asaunify.entity.LoginHistory;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.repos.AuditLogRepo;
import com.asa.asaunify.repos.LoginHistoryRepo;
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
public class AuditReportService {

    private final AuditLogRepo auditLogRepository;
    private final LoginHistoryRepo loginHistoryRepository;
    private final UserRepo userRepository;

    // ─── Audit Log Report ─────────────────────────────────────

    public List<AuditLogDTO> getAuditLogs(
            UUID userId,
            ActionType actionType,
            String module,
            String objectType,
            LocalDateTime from,
            LocalDateTime to) {

        // Base query — date range
        LocalDateTime effectiveFrom = from != null
                ? from : LocalDateTime.now().minusMonths(1);
        LocalDateTime effectiveTo = to != null
                ? to : LocalDateTime.now();

        List<AuditLog> logs;

        if (userId != null) {
            // Filter by specific user
            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("User not found"));
            logs = auditLogRepository
                    .findByUserAndTimestampBetweenOrderByTimestampDesc(
                            user, effectiveFrom, effectiveTo
                    );
        } else if (actionType != null) {
            logs = auditLogRepository
                    .findByActionTypeAndTimestampBetweenOrderByTimestampDesc(
                            actionType, effectiveFrom, effectiveTo
                    );
        } else if (module != null) {
            logs = auditLogRepository
                    .findByModuleAndTimestampBetweenOrderByTimestampDesc(
                            module, effectiveFrom, effectiveTo
                    );
        } else {
            logs = auditLogRepository
                    .findByTimestampBetweenOrderByTimestampDesc(
                            effectiveFrom, effectiveTo
                    );
        }

        // Apply remaining filters in memory
        return logs.stream()
                .filter(l -> objectType == null ||
                        objectType.equals(l.getObjectType()))
                .map(this::toAuditDTO)
                .collect(Collectors.toList());
    }

    // All actions on a specific object
    // e.g. full history of request CN-001
    public List<AuditLogDTO> getObjectHistory(
            String objectType,
            String objectId) {
        return auditLogRepository
                .findByObjectTypeAndObjectIdOrderByTimestampDesc(
                        objectType, objectId
                )
                .stream()
                .map(this::toAuditDTO)
                .collect(Collectors.toList());
    }

    // Recent logs — admin dashboard feed
    public List<AuditLogDTO> getRecentLogs(int limit) {
        return auditLogRepository
                .findRecentLogs(limit)
                .stream()
                .map(this::toAuditDTO)
                .collect(Collectors.toList());
    }

    // ─── Login History Report ─────────────────────────────────

    public List<LoginHistoryDTO> getLoginHistory(
            UUID userId,
            LocalDateTime from,
            LocalDateTime to) {

        LocalDateTime effectiveFrom = from != null
                ? from : LocalDateTime.now().minusMonths(1);
        LocalDateTime effectiveTo = to != null
                ? to : LocalDateTime.now();

        List<LoginHistory> history;

        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("User not found"));
            history = loginHistoryRepository
                    .findByUserAndLoginAtBetweenOrderByLoginAtDesc(
                            user, effectiveFrom, effectiveTo
                    );
        } else {
            history = loginHistoryRepository
                    .findByLoginAtBetweenOrderByLoginAtDesc(
                            effectiveFrom, effectiveTo
                    );
        }

        return history.stream()
                .map(this::toLoginDTO)
                .collect(Collectors.toList());
    }

    // Failed login attempts — security monitoring
    public List<LoginHistoryDTO> getFailedLogins(
            LocalDateTime from,
            LocalDateTime to) {

        LocalDateTime effectiveFrom = from != null
                ? from : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveTo = to != null
                ? to : LocalDateTime.now();

        return loginHistoryRepository
                .findByLoginAtBetweenOrderByLoginAtDesc(
                        effectiveFrom, effectiveTo
                )
                .stream()
                .filter(l -> "FAILED".equals(l.getLoginStatus()))
                .map(this::toLoginDTO)
                .collect(Collectors.toList());
    }

    // ─── DTO Mappers ──────────────────────────────────────────

    private AuditLogDTO toAuditDTO(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .userId(
                        log.getUser() != null
                                ? log.getUser().getId() : null
                )
                .userFullName(
                        log.getUser() != null
                                ? log.getUser().getFullName() : null
                )
                .userRole(
                        log.getUser() != null
                                ? log.getUser().getRole().name() : null
                )
                .actionType(log.getActionType().name())
                .objectType(log.getObjectType())
                .objectId(log.getObjectId())
                .oldValue(
                        log.getOldValue() != null
                                ? log.getOldValue().toString() : null
                )
                .newValue(
                        log.getNewValue() != null
                                ? log.getNewValue().toString() : null
                )
                .ipAddress(log.getIpAddress())
                .module(log.getModule())
                .build();
    }

    private LoginHistoryDTO toLoginDTO(LoginHistory history) {
        return LoginHistoryDTO.builder()
                .id(history.getId())
                .userId(history.getUser().getId())
                .userFullName(history.getUser().getFullName())
                .userRole(history.getUser().getRole().name())
                .loginAt(history.getLoginAt())
                .logoutAt(history.getLogoutAt())
                .sessionDurationMinutes(history.getSessionDurationMinutes())
                .ipAddress(history.getIpAddress())
                .browser(history.getBrowser())
                .device(history.getDevice())
                .loginStatus(history.getLoginStatus())
                .build();
    }

    // ─── DTOs ─────────────────────────────────────────────────

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class AuditLogDTO {
        private UUID id;
        private LocalDateTime timestamp;
        private UUID userId;
        private String userFullName;
        private String userRole;
        private String actionType;
        private String objectType;
        private String objectId;
        private String oldValue;
        private String newValue;
        private String ipAddress;
        private String module;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class LoginHistoryDTO {
        private UUID id;
        private UUID userId;
        private String userFullName;
        private String userRole;
        private LocalDateTime loginAt;
        private LocalDateTime logoutAt;
        private Long sessionDurationMinutes;
        private String ipAddress;
        private String browser;
        private String device;
        private String loginStatus;
    }
}