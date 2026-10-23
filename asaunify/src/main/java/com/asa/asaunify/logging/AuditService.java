package com.asa.asaunify.logging;

import com.asa.asaunify.entity.AuditLog;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.repos.AuditLogRepo;
import com.asa.asaunify.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class AuditService {

    private final AuditLogRepo auditLogRepository;
    private final Executor executor;

    public AuditService(AuditLogRepo auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
        // Small dedicated pool so audit writes never block the request thread.
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "audit-writer");
            t.setDaemon(true);
            return t;
        });
    }

    // The client IP is read on the calling thread: the HttpServletRequest is
    // recycled once the response completes, so reading it from an @Async
    // thread returned wrong values or threw.
    public void log(
            User user,
            ActionType actionType,
            String objectType,
            String objectId,
            String module,
            Map<String, Object> oldValue,
            Map<String, Object> newValue,
            HttpServletRequest request) {

        final String ip = ClientIp.of(request);

        executor.execute(() -> {
            try {
                AuditLog auditLog = AuditLog.builder()
                        .user(user)
                        .actionType(actionType)
                        .objectType(objectType)
                        .objectId(objectId)
                        .module(module)
                        .oldValue(oldValue)
                        .newValue(newValue)
                        .ipAddress(ip)
                        .build();

                auditLogRepository.save(auditLog);

            } catch (Exception e) {
                // Never let audit logging break the main operation
                log.error("Audit log failed: {}", e.getMessage());
            }
        });
    }

    // Overload for when you don't need old/new values
    public void log(
            User user,
            ActionType actionType,
            String objectType,
            String objectId,
            String module,
            HttpServletRequest request) {
        log(user, actionType, objectType, objectId, module, null, null, request);
    }
}
