package com.asa.asaunify.services;



import com.asa.asaunify.repos.DepartmentRepo;
import com.asa.asaunify.repos.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.asa.asaunify.entity.Department;
import com.asa.asaunify.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final DepartmentRepo departmentRepository;
    private final UserRepo userRepository;


    @Transactional
    public Department createDepartment(String name, UUID headUserId) {
        if (departmentRepository.existsByName(name)) {
            throw new IllegalArgumentException(
                    "Department already exists: " + name
            );
        }

        Department department = Department.builder()
                .name(name)
//                .headUserId(headUserId)
                .build();

        // Validate head user exists and has DEPARTMENT_HEAD role
//        if (headUserId != null) {
//            User head = userRepository.findById(headUserId)
//                    .orElseThrow(() ->
//                            new IllegalArgumentException("Head user not found"));
//
//            if (!head.hasRole(com.asa.asaunify.enums.Role.DEPARTMENT_HEAD)) {
//                throw new IllegalArgumentException(
//                        "Assigned head user does not have DEPARTMENT_HEAD role"
//                );
//            }
//        }

        if (headUserId != null) {
            userRepository.findById(headUserId)
                    .ifPresentOrElse(
                            user -> department.setHeadUserId(headUserId),
                            () -> { throw new IllegalArgumentException("Head user not found"); }
                    );
        }



        return departmentRepository.save(department);
    }

    // ─── Read ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Department getDepartmentById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Department not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    // Get the department head user
    public User getDepartmentHead(UUID departmentId) {
        Department department = getDepartmentById(departmentId);

        if (department.getHeadUserId() == null) {
            throw new IllegalArgumentException(
                    "Department has no assigned head"
            );
        }

        return userRepository.findById(department.getHeadUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Department head user not found"));
    }

    // ─── Update ───────────────────────────────────────────────

    @Transactional
    public Department updateDepartment(
            UUID id,
            String name,
            UUID headUserId) {

        Department department = getDepartmentById(id);

        if (name != null) {
            if (departmentRepository.existsByName(name) &&
                    !department.getName().equals(name)) {
                throw new IllegalArgumentException(
                        "Department name already in use: " + name
                );
            }
            department.setName(name);
        }

        if (headUserId != null) {
            User head = userRepository.findById(headUserId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Head user not found"));

            if (!head.hasRole(com.asa.asaunify.enums.Role.DEPARTMENT_HEAD)) {
                throw new IllegalArgumentException(
                        "Assigned head user does not have DEPARTMENT_HEAD role"
                );
            }
            department.setHeadUserId(headUserId);
        }

        return departmentRepository.save(department);
    }

    // ─── Delete ───────────────────────────────────────────────

    @Transactional
    public void deleteDepartment(UUID id) {
        Department department = getDepartmentById(id);

        // Check if any users belong to this department
        List<User> members = userRepository.findByDepartment(department);
        if (!members.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete department with active members. " +
                            "Reassign or deactivate members first."
            );
        }

        departmentRepository.delete(department);
    }
}