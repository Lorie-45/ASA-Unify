package com.asa.asaunify.controllers;



import jakarta.validation.Valid;
import com.asa.asaunify.dtos.DepartmentDto;
import com.asa.asaunify.services.DepartmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Tag(name = "Department")
public class DepartmentController {

    private final DepartmentService departmentService;

    // ─── Create — Admin only ──────────────────────────────────

    // POST /api/departments
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(departmentService.createDepartment(request.getName()));
    }

    // ─── Get all ──────────────────────────────────────────────

    // GET /api/departments
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DepartmentDto>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    // ─── Get by id ────────────────────────────────────────────

    // GET /api/departments/{id}
    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDto> getDepartmentById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(departmentService.getDepartmentDtoById(id));
    }

    // ─── Update — Admin only ──────────────────────────────────
    // Only the name is editable; the head is derived from user role/department.

    // PATCH /api/departments/{id}
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request) {

        return ResponseEntity.ok(
                departmentService.updateDepartment(id, request.getName())
        );
    }

    // ─── Delete — Admin only ──────────────────────────────────

    // DELETE /api/departments/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDepartment(
            @PathVariable UUID id) {

        departmentService.deleteDepartment(id);
        return ResponseEntity.ok().build();
    }

    // ─── Request bodies ───────────────────────────────────────

    @Getter
    @Setter
    public static class CreateDepartmentRequest {
        @NotBlank(message = "Department name is required")
        private String name;
    }

    @Getter
    @Setter
    public static class UpdateDepartmentRequest {
        private String name;
    }
}
