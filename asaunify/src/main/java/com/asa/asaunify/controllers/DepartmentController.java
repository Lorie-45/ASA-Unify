package com.asa.asaunify.controllers;



import com.asa.asaunify.entity.Department;
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
    public ResponseEntity<Department> createDepartment(
            @RequestBody CreateDepartmentRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(departmentService.createDepartment(
                        request.getName(),
                        request.getHeadUserId()
                ));
    }

    // ─── Get all ──────────────────────────────────────────────

    // GET /api/departments
    public ResponseEntity<List<Department>> getAllDepartments() {
        return ResponseEntity.ok(
                departmentService.getAllDepartments()
        );
    }

    // ─── Get by id ────────────────────────────────────────────

    // GET /api/departments/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Department> getDepartmentById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                departmentService.getDepartmentById(id)
        );
    }

    // ─── Update — Admin only ──────────────────────────────────

    // PATCH /api/departments/{id}
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Department> updateDepartment(
            @PathVariable UUID id,
            @RequestBody UpdateDepartmentRequest request) {

        return ResponseEntity.ok(
                departmentService.updateDepartment(
                        id,
                        request.getName(),
                        request.getHeadUserId()
                )
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
        private UUID headUserId;
    }

    @Getter
    @Setter
    public static class UpdateDepartmentRequest {
        private String name;
        private UUID headUserId;
    }
}