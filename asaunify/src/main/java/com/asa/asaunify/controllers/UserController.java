package com.asa.asaunify.controllers;


import com.asa.asaunify.dtos.CreateUserRequest;
import com.asa.asaunify.dtos.UpdateUserRequest;
import com.asa.asaunify.dtos.UserDto;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.services.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    // ─── Create user — Admin only ─────────────────────────────

    // POST /api/users
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.createUser(
                        request, currentUser, httpRequest
                ));
    }

    // ─── Get all users — Admin and Auditor ───────────────────

    // GET /api/users
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ─── Get user by id ───────────────────────────────────────

    // GET /api/users/{id}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<UserDto> getUserById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // ─── Get current logged in user ───────────────────────────

    // GET /api/users/me
    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService
                .findUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(userService.toDTO(user));
    }

    // ─── Get users by department ──────────────────────────────

    // GET /api/users/department/{departmentId}
    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'DEPARTMENT_HEAD')")
    public ResponseEntity<List<UserDto>> getUsersByDepartment(
            @PathVariable UUID departmentId) {
        return ResponseEntity.ok(
                userService.getUsersByDepartment(departmentId)
        );
    }

    // ─── Get users by role ────────────────────────────────────

    // GET /api/users/role/{role}
    @GetMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getUsersByRole(
            @PathVariable Role role) {
        return ResponseEntity.ok(userService.getUsersByRole(role));
    }

    // ─── Update user — Admin only ─────────────────────────────

    // PATCH /api/users/{id}
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        return ResponseEntity.ok(
                userService.updateUser(id, request, currentUser, httpRequest)
        );
    }

    // ─── Deactivate user — Admin only ─────────────────────────

    // DELETE /api/users/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User currentUser = userService
                .findUserByEmail(userDetails.getUsername());

        userService.deactivateUser(id, currentUser, httpRequest);
        return ResponseEntity.ok().build();
    }
}