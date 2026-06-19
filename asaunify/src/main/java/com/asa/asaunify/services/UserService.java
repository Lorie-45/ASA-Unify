package com.asa.asaunify.services;


import com.asa.asaunify.dtos.CreateUserRequest;
import com.asa.asaunify.dtos.UpdateUserRequest;
import com.asa.asaunify.dtos.UserDto;
import com.asa.asaunify.entity.Department;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.DepartmentRepo;
import com.asa.asaunify.repos.UserRepo;
import com.asa.asaunify.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepo userRepository;
    private final DepartmentRepo departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public UserDto createUser(
            CreateUserRequest request,
            User createdBy,
            HttpServletRequest httpRequest) {

        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already in use: " + request.getEmail()
            );
        }

        // Resolve department
        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository
                    .findById(request.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Department not found"
                    ));
        }

        // Enforce department requirement based on role
        validateDepartmentForRole(request.getRole(), department);

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .department(department)
                .isActive(true)
                .build();

        User saved = userRepository.save(user);

        auditService.log(
                createdBy,
                ActionType.USER_CREATED,
                "USER",
                saved.getId().toString(),
                "USERS",
                httpRequest
        );

        return toDTO(saved);
    }


    public UserDto getUserById(UUID id) {
        return toDTO(findUserById(id));
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<UserDto> getUsersByDepartment(UUID departmentId) {
        Department department = departmentRepository
                .findById(departmentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Department not found"));

        return userRepository.findByDepartment(department)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<UserDto> getUsersByRole(Role role) {
        return userRepository.findByRole(role)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto updateUser(
            UUID id,
            UpdateUserRequest request,
            User updatedBy,
            HttpServletRequest httpRequest) {

        User user = findUserById(id);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            if (userRepository.existsByEmail(request.getEmail()) &&
                    !user.getEmail().equals(request.getEmail())) {
                throw new IllegalArgumentException(
                        "Email already in use: " + request.getEmail()
                );
            }
            user.setEmail(request.getEmail());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository
                    .findById(request.getDepartmentId())
                    .orElseThrow(() ->
                            new IllegalArgumentException("Department not found"));
            user.setDepartment(department);
        }
        if (request.getIsActive() != null) {
            user.setActive(request.getIsActive());
        }

        User saved = userRepository.save(user);

        auditService.log(
                updatedBy,
                ActionType.USER_UPDATED,
                "USER",
                saved.getId().toString(),
                "USERS",
                httpRequest
        );

        return toDTO(saved);
    }


    @Transactional
    public void deactivateUser(
            UUID id,
            User deactivatedBy,
            HttpServletRequest httpRequest) {

        User user = findUserById(id);
        user.setActive(false);
        userRepository.save(user);

        auditService.log(
                deactivatedBy,
                ActionType.USER_DEACTIVATED,
                "USER",
                user.getId().toString(),
                "USERS",
                httpRequest
        );
    }


    public User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + id));
    }

    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + email));
    }

    private void validateDepartmentForRole(Role role, Department department) {
        // ADMIN and AUDITOR do not need a department
        if (role == Role.ADMIN || role == Role.AUDITOR) return;

        // All other roles must belong to a department
        if (department == null) {
            throw new IllegalArgumentException(
                    "Department is required for role: " + role
            );
        }
    }


    public UserDto toDTO(User user) {
        return UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .departmentId(
                        user.getDepartment() != null
                                ? user.getDepartment().getId()
                                : null
                )
                .departmentName(
                        user.getDepartment() != null
                                ? user.getDepartment().getName()
                                : null
                )
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

}
