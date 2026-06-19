package com.asa.asaunify.controllers;




import com.asa.asaunify.dtos.RequestResponseDto;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.services.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final CaseReportService caseReportService;
    private final DashboardReportService dashboardReportService;
    private final UserReportService userReportService;
    private final AuditReportService auditReportService;
    private final ExportService exportService;
    private final UserService userService;

    // ─── Case Reports ─────────────────────────────────────────

    // GET /api/reports/cases/all
    @GetMapping("/cases/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<RequestResponseDto>> getAllCases(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                caseReportService.getAllCases(status, type, from, to)
        );
    }

    // GET /api/reports/cases/my
    @GetMapping("/cases/my")
    public ResponseEntity<List<RequestResponseDto>> getMyCases(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestType type,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                caseReportService.getMyCases(currentUser, status, type)
        );
    }

    // GET /api/reports/cases/department
    @GetMapping("/cases/department")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<List<RequestResponseDto>> getDepartmentCases(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                caseReportService.getDepartmentCases(
                        currentUser, status, type, from, to
                )
        );
    }

    // GET /api/reports/cases/completed
    @GetMapping("/cases/completed")
    public ResponseEntity<List<RequestResponseDto>> getCompletedCases(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                caseReportService.getCompletedCases(currentUser, from, to)
        );
    }

    // GET /api/reports/cases/rejected
    @GetMapping("/cases/rejected")
    public ResponseEntity<List<RequestResponseDto>> getRejectedCases(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                caseReportService.getRejectedCases(currentUser, from, to)
        );
    }

    // GET /api/reports/cases/overdue
    @GetMapping("/cases/overdue")
    public ResponseEntity<List<RequestResponseDto>> getOverdueCases(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                caseReportService.getOverdueCases(currentUser)
        );
    }

    // ─── Dashboard Report ─────────────────────────────────────

    // GET /api/reports/dashboard
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'DEPARTMENT_HEAD')")
    public ResponseEntity<DashboardReportService.DashboardSummaryDTO>
    getDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                dashboardReportService.getSummary(currentUser, from, to)
        );
    }

    // ─── User Activity Reports ────────────────────────────────

    // GET /api/reports/users/activity
    @GetMapping("/users/activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<UserReportService.UserActivityDTO>>
    getUserActivity(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                userReportService.getUserActivityReport(from, to)
        );
    }

    // GET /api/reports/users/{userId}/activity
    @GetMapping("/users/{userId}/activity")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<UserReportService.UserActivityDTO>
    getSingleUserActivity(
            @PathVariable UUID userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                userReportService.getUserActivity(userId, from, to)
        );
    }

    // GET /api/reports/users/{userId}/tasks
    @GetMapping("/users/{userId}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<UserReportService.TaskAssignmentDTO>>
    getTaskAssignments(
            @PathVariable UUID userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                userReportService.getTaskAssignmentReport(userId, from, to)
        );
    }

    // ─── Audit Reports ────────────────────────────────────────

    // GET /api/reports/audit/logs
    @GetMapping("/audit/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<AuditReportService.AuditLogDTO>>
    getAuditLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                auditReportService.getAuditLogs(
                        userId, actionType, module, objectType, from, to
                )
        );
    }

    // GET /api/reports/audit/history/{objectType}/{objectId}
    @GetMapping("/audit/history/{objectType}/{objectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<AuditReportService.AuditLogDTO>>
    getObjectHistory(
            @PathVariable String objectType,
            @PathVariable String objectId) {

        return ResponseEntity.ok(
                auditReportService.getObjectHistory(objectType, objectId)
        );
    }

    // GET /api/reports/audit/recent
    @GetMapping("/audit/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<AuditReportService.AuditLogDTO>>
    getRecentLogs(
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(
                auditReportService.getRecentLogs(limit)
        );
    }

    // GET /api/reports/audit/logins
    @GetMapping("/audit/logins")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<AuditReportService.LoginHistoryDTO>>
    getLoginHistory(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                auditReportService.getLoginHistory(userId, from, to)
        );
    }

    // GET /api/reports/audit/failed-logins
    @GetMapping("/audit/failed-logins")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<AuditReportService.LoginHistoryDTO>>
    getFailedLogins(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return ResponseEntity.ok(
                auditReportService.getFailedLogins(from, to)
        );
    }

    // ─── Export ───────────────────────────────────────────────

    // GET /api/reports/export?format=excel|csv|pdf
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'DEPARTMENT_HEAD')")
    public ResponseEntity<byte[]> exportCases(
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        // Fetch data using same scoped query as case reports
        List<RequestResponseDto> data = caseReportService
                .getAllCases(status, type, from, to);

        try {
            byte[] bytes;
            MediaType mediaType;
            String filename;

            switch (format.toLowerCase()) {
                case "csv" -> {
                    bytes = exportService.exportRequestsToCsv(data);
                    mediaType = MediaType.parseMediaType("text/csv");
                    filename = "requests.csv";
                }
                case "pdf" -> {
                    bytes = exportService.exportRequestsToPdf(
                            data, "ASAUnify — Requests Report"
                    );
                    mediaType = MediaType.APPLICATION_PDF;
                    filename = "requests.pdf";
                }
                default -> {
                    bytes = exportService.exportRequestsToExcel(data);
                    mediaType = MediaType.parseMediaType(
                            "application/vnd.openxmlformats-" +
                                    "officedocument.spreadsheetml.sheet"
                    );
                    filename = "requests.xlsx";
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(filename)
                            .build()
            );

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(mediaType)
                    .body(bytes);

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
}