package com.asa.asaunify.controllers;



import com.asa.asaunify.dtos.*;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.services.RequestService;
import com.asa.asaunify.services.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Requests")
public class RequestController {

    private final RequestService requestService;
    private final UserService userService;

    // ─── Create draft ─────────────────────────────────────────

    // POST /api/requests
    @PostMapping
    public ResponseEntity<RequestResponseDto> createDraft(
            @Valid @RequestBody CreateRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(requestService.createDraft(
                        dto, currentUser, httpRequest
                ));
    }

    // ─── Submit draft ─────────────────────────────────────────

    // POST /api/requests/{id}/submit
    @PostMapping("/{id}/submit")
    public ResponseEntity<RequestResponseDto> submitRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                requestService.submitRequest(id, currentUser, httpRequest)
        );
    }

    // ─── Approve or Reject ────────────────────────────────────

    // POST /api/requests/{id}/action
    @PostMapping("/{id}/action")
    public ResponseEntity<RequestResponseDto> processAction(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalActionDto dto,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                requestService.processAction(
                        id, dto, currentUser, httpRequest
                )
        );
    }

    // PATCH /api/requests/{id}
    @PatchMapping("/{id}")
    public ResponseEntity<RequestResponseDto> updateDraft(
            @PathVariable UUID id,
            @RequestBody UpdateRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService.findUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(
                requestService.updateDraft(id, dto, currentUser, httpRequest)
        );
    }

    // ─── Cancel ───────────────────────────────────────────────

    // POST /api/requests/{id}/cancel
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', " +
            "'RM', 'MSME_OFFICER')")
    public ResponseEntity<RequestResponseDto> cancelRequest(
            @PathVariable UUID id,
            @RequestBody CancelRequestBody body,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                requestService.cancelRequest(
                        id, body.getReason(), currentUser, httpRequest
                )
        );
    }

    // ─── Assign Driver ────────────────────────────────────────

    // POST /api/requests/{id}/assign-driver
    @PostMapping("/{id}/assign-driver")
    @PreAuthorize("hasRole('FLEET_MANAGER')")
    public ResponseEntity<Void> assignDriver(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDriverDto dto,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        requestService.assignDriver(id, dto, currentUser, httpRequest);
        return ResponseEntity.ok().build();
    }

    // GET /api/requests/my-trips
    @GetMapping("/my-trips")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<VehicleTripAssignmentDto>> getMyTrips(
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(requestService.getMyTrips(currentUser));
    }

    // GET /api/requests/loans
    @GetMapping("/loans")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'MSME_OFFICER', 'RM', 'CREDIT_OFFICER', 'ADMIN')")
    public ResponseEntity<List<RequestResponseDto>> getLoanRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.findUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(requestService.getLoanRequests(currentUser));
    }

    // ─── Mark trip as seen (Driver only) ─────────────────────

    // POST /api/requests/{id}/seen
    @PostMapping("/{id}/seen")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Void> markTripSeen(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        requestService.markTripSeen(id, currentUser);
        return ResponseEntity.ok().build();
    }

    // ─── Get my requests ──────────────────────────────────────

    // GET /api/requests/my
    @GetMapping("/my")
    public ResponseEntity<List<RequestResponseDto>> getMyRequests(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                requestService.getMyRequests(currentUser)
        );
    }

    // ─── Get department requests ──────────────────────────────

    // GET /api/requests/department
    @GetMapping("/department")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<List<RequestResponseDto>> getDepartmentRequests(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                requestService.getDepartmentRequests(currentUser)
        );
    }

    // ─── Get pending approvals for current user's role ────────

    // GET /api/requests/pending
    @GetMapping("/pending")
    public ResponseEntity<List<RequestResponseDto>> getPendingForRole(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                requestService.getPendingForRole(currentUser)
        );
    }





    @GetMapping("/role/{role}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserDto>> getUsersByRole(
            @PathVariable Role role) {
        try {
            log.info("getUsersByRole called with role: {}", role);
            List<UserDto> result = userService.getUsersByRole(role);
            log.info("getUsersByRole returning {} users", result.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("getUsersByRole failed", e);
            throw e;
        }
    }





    // ─── Get all requests — Admin and Auditor ─────────────────

    // GET /api/requests
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<RequestResponseDto>> getAllRequests() {
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    // ─── Get single request ───────────────────────────────────

    // GET /api/requests/{id}
    @GetMapping("/{id}")
    public ResponseEntity<RequestResponseDto> getRequestById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(requestService.getRequestById(id));
    }

    // ─── Inner class for cancel body ──────────────────────────

    @lombok.Getter
    @lombok.Setter
    public static class CancelRequestBody {
        private String reason;
    }
}