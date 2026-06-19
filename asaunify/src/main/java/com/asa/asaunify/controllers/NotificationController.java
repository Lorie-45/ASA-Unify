package com.asa.asaunify.controllers;




import com.asa.asaunify.dtos.NotificationDto;
import com.asa.asaunify.dtos.NotificationSummaryDto;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.services.NotificationService;
import com.asa.asaunify.services.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    // ─── Get summary (bell badge + last 10) ───────────────────

    // GET /api/notifications/summary
    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDto> getSummary(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                notificationService.getSummary(currentUser)
        );
    }

    // ─── Get all notifications ────────────────────────────────

    // GET /api/notifications
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getAllNotifications(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                notificationService.getAllForUser(currentUser)
        );
    }

    // ─── Mark all as read ─────────────────────────────────────

    // POST /api/notifications/read-all
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        notificationService.markAllAsRead(currentUser);
        return ResponseEntity.ok().build();
    }

    // ─── Mark single as read ──────────────────────────────────

    // POST /api/notifications/{id}/read
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}