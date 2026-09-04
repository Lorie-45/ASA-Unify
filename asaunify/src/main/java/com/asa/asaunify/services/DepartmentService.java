package com.asa.asaunify.services;


import com.asa.asaunify.dtos.DepartmentDto;
import com.asa.asaunify.entity.Department;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.exceptions.DuplicateResourceException;
import com.asa.asaunify.exceptions.ResourceNotFoundException;
import com.asa.asaunify.repos.DepartmentRepo;
import com.asa.asaunify.repos.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final DepartmentRepo departmentRepository;
    private final UserRepo userRepository;

    // ─── Create ───────────────────────────────────────────────
    // A department is created with just a name. Its head is whoever is made a
    // DEPARTMENT_HEAD in this department via user management — there is no
    // separate head field to keep in sync.

    @Transactional
    public DepartmentDto createDepartment(String name) {
        if (departmentRepository.existsByName(name)) {
            throw new DuplicateResourceException("Department already exists");
        }

        Department department = Department.builder()
                .name(name)
                .build();

        return toDto(departmentRepository.save(department));
    }

    // ─── Read ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Department getDepartmentById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Department not found"));
    }

    @Transactional(readOnly = true)
    public DepartmentDto getDepartmentDtoById(UUID id) {
        return toDto(getDepartmentById(id));
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // The department head, derived from user role + department.
    @Transactional(readOnly = true)
    public User getDepartmentHead(UUID departmentId) {
        Department department = getDepartmentById(departmentId);
        return findActiveHead(department)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department has no assigned head"));
    }

    // ─── Update ───────────────────────────────────────────────
    // Only the name is editable here. The head is managed through the user's
    // role and department.

    @Transactional
    public DepartmentDto updateDepartment(UUID id, String name) {
        Department department = getDepartmentById(id);

        if (name != null) {
            if (departmentRepository.existsByName(name) &&
                    !department.getName().equals(name)) {
                throw new DuplicateResourceException("Department name already in use");
            }
            department.setName(name);
        }

        return toDto(departmentRepository.save(department));
    }

    // ─── Delete ───────────────────────────────────────────────

    @Transactional
    public void deleteDepartment(UUID id) {
        Department department = getDepartmentById(id);

        List<User> members = userRepository.findByDepartment(department);
        if (!members.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete department with active members. " +
                            "Reassign or deactivate members first."
            );
        }

        departmentRepository.delete(department);
    }

    // ─── Helpers ──────────────────────────────────────────────

    private Optional<User> findActiveHead(Department department) {
        return userRepository
                .findByDepartmentAndRoleAndIsActiveTrue(department, Role.DEPARTMENT_HEAD)
                .stream()
                .findFirst();
    }

    private DepartmentDto toDto(Department department) {
        User head = findActiveHead(department).orElse(null);
        return DepartmentDto.builder()
                .id(department.getId())
                .name(department.getName())
                .headUserId(head != null ? head.getId() : null)
                .headName(head != null ? head.getFullName() : null)
                .build();
    }
}
