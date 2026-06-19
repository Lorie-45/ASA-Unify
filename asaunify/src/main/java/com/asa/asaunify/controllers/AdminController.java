package com.asa.asaunify.controllers;



import com.asa.asaunify.dtos.RequestResponseDto;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.services.AuditReportService;
import com.asa.asaunify.services.DashboardReportService;
import com.asa.asaunify.services.RequestService;
import com.asa.asaunify.services.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {

    private final RequestService requestService;
    private final UserService userService;
    private final DashboardReportService dashboardReportService;
    private final AuditReportService auditReportService;

    // ─── Admin Dashboard ──────────────────────────────────────

    // GET /api/admin/dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardReportService.DashboardSummaryDTO>
    getAdminDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                dashboardReportService.getSummary(currentUser, null, null)
        );
    }

    // ─── All requests ─────────────────────────────────────────

    // GET /api/admin/requests
    @GetMapping("/requests")
    public ResponseEntity<List<RequestResponseDto>> getAllRequests() {
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    // ─── Recent audit activity ────────────────────────────────

    // GET /api/admin/activity
    @GetMapping("/activity")
    public ResponseEntity<List<AuditReportService.AuditLogDTO>>
    getRecentActivity() {

        return ResponseEntity.ok(
                auditReportService.getRecentLogs(50)
        );
    }
}