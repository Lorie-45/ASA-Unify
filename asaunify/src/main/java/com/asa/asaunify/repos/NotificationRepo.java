package com.asa.asaunify.repos;


import com.asa.asaunify.entity.Notification;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepo extends JpaRepository<Notification, UUID> {

    // All notifications for a user — newest first
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    // All unread notifications for a user
    // Powers the notification bell badge count
    List<Notification> findByUserAndIsReadFalseOrderByCreatedAtDesc(User user);

    // All read notifications for a user
    List<Notification> findByUserAndIsReadTrueOrderByCreatedAtDesc(User user);

    // Count unread notifications — used for the bell badge number
    long countByUserAndIsReadFalse(User user);

    // Mark all notifications as read for a user
    // Called when user opens the notification panel
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true " +
            "WHERE n.user = :user AND n.isRead = false")
    void markAllAsRead(@Param("user") User user);

    // Mark a single notification as read
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id")
    void markAsRead(@Param("id") UUID id);

    // All notifications related to a specific request
    // Used to show full activity history on request detail page
    List<Notification> findByRequestIdOrderByCreatedAtDesc(UUID requestId);

    // All notifications related to a specific memo
    List<Notification> findByMemoIdOrderByCreatedAtDesc(UUID memoId);

    // Notifications by action type — useful for filtering
    List<Notification> findByUserAndActionTypeOrderByCreatedAtDesc(
            User user,
            ActionType actionType
    );
}