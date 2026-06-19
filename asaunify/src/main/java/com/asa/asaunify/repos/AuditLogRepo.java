package com.asa.asaunify.repos;

import com.asa.asaunify.entity.AuditLog;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, UUID> {

    // All logs for a specific user — newest first
    List<AuditLog> findByUserOrderByTimestampDesc(User user);

    // All logs by action type
    List<AuditLog> findByActionTypeOrderByTimestampDesc(ActionType actionType);

    // All logs for a specific object
    // e.g. all actions performed on REQUEST with id X
    List<AuditLog> findByObjectTypeAndObjectIdOrderByTimestampDesc(
            String objectType,
            String objectId
    );

    // All logs in a date range — primary report query
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime from,
            LocalDateTime to
    );

    // Logs for a specific user in a date range
    List<AuditLog> findByUserAndTimestampBetweenOrderByTimestampDesc(
            User user,
            LocalDateTime from,
            LocalDateTime to
    );

    // Logs by module in a date range
    // e.g. all actions in "REQUESTS" module this month
    List<AuditLog> findByModuleAndTimestampBetweenOrderByTimestampDesc(
            String module,
            LocalDateTime from,
            LocalDateTime to
    );

    // Logs by action type in a date range
    List<AuditLog> findByActionTypeAndTimestampBetweenOrderByTimestampDesc(
            ActionType actionType,
            LocalDateTime from,
            LocalDateTime to
    );

    // Count actions per user in a period — user activity report
    @Query("SELECT a.user, COUNT(a) as actionCount FROM AuditLog a " +
            "WHERE a.timestamp BETWEEN :from AND :to " +
            "GROUP BY a.user ORDER BY actionCount DESC")
    List<Object[]> countActionsByUserInPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // Most recent logs — used on admin dashboard
    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC LIMIT :limit")
    List<AuditLog> findRecentLogs(@Param("limit") int limit);

    // All logs by IP address — security monitoring
    List<AuditLog> findByIpAddressOrderByTimestampDesc(String ipAddress);
}