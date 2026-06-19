package com.asa.asaunify.logging;

import com.asa.asaunify.entity.AuditLog;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.repos.AuditLogRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepo auditLogRepository;


    @Async
    public void log(
            User user,
            ActionType actionType,
            String objectType,
            String objectId,
            String module,
            Map<String, Object> oldValue,
            Map<String, Object> newValue,
            HttpServletRequest request) {

        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .actionType(actionType)
                    .objectType(objectType)
                    .objectId(objectId)
                    .module(module)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .ipAddress(getClientIp(request))
                    .build();

            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            // Never let audit logging break the main operation
            log.error("Audit log failed: {}", e.getMessage());
        }
    }

    // Overload for when you don't need old/new values
    @Async
    public void log(
            User user,
            ActionType actionType,
            String objectType,
            String objectId,
            String module,
            HttpServletRequest request) {
        log(user, actionType, objectType, objectId, module, null, null, request);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}