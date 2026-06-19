package com.asa.asaunify.services;


import com.asa.asaunify.dtos.RequestResponseDto;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.RequestStatus;
import com.asa.asaunify.enums.RequestType;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.repos.DepartmentRepo;
import com.asa.asaunify.repos.RequestRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseReportService {

    private final RequestRepo requestRepository;
    private final DepartmentRepo departmentRepository;
    private final RequestService requestService;

    // ─── All Cases ────────────────────────────────────────────

    // Admin and Auditor only — all requests in the system
    public List<RequestResponseDto> getAllCases(
            RequestStatus status,
            RequestType type,
            LocalDateTime from,
            LocalDateTime to) {

        List<Request> requests = requestRepository.findAll();

        return requests.stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> type == null || r.getType() == type)
                .filter(r -> from == null ||
                        !r.getCreatedAt().isBefore(from))
                .filter(r -> to == null ||
                        !r.getCreatedAt().isAfter(to))
                .map(requestService::toDTO)
                .collect(Collectors.toList());
    }

    // ─── My Cases ─────────────────────────────────────────────

    // Any user — their own requests only
    public List<RequestResponseDto> getMyCases(
            User user,
            RequestStatus status,
            RequestType type) {

        return requestRepository
                .findByInitiatorOrderByCreatedAtDesc(user)
                .stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> type == null || r.getType() == type)
                .map(requestService::toDTO)
                .collect(Collectors.toList());
    }

    // ─── Department Cases ─────────────────────────────────────

    // Department Head — all requests in their department
    public List<RequestResponseDto> getDepartmentCases(
            User deptHead,
            RequestStatus status,
            RequestType type,
            LocalDateTime from,
            LocalDateTime to) {

        if (deptHead.getDepartment() == null) {
            throw new IllegalArgumentException(
                    "User does not belong to a department"
            );
        }

        return requestRepository
                .findByDepartmentOrderByCreatedAtDesc(deptHead.getDepartment())
                .stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> type == null || r.getType() == type)
                .filter(r -> from == null ||
                        !r.getCreatedAt().isBefore(from))
                .filter(r -> to == null ||
                        !r.getCreatedAt().isAfter(to))
                .map(requestService::toDTO)
                .collect(Collectors.toList());
    }

    // ─── Completed Cases ──────────────────────────────────────

    public List<RequestResponseDto> getCompletedCases(
            User currentUser,
            LocalDateTime from,
            LocalDateTime to) {

        List<Request> all;

        // Scope based on role
        if (currentUser.getRole() == Role.ADMIN ||
                currentUser.getRole() == Role.AUDITOR) {
            // See all completed
            all = requestRepository.findByStatusOrderByCreatedAtDesc(
                    RequestStatus.COMPLETED
            );
        } else if (currentUser.getRole() == Role.DEPARTMENT_HEAD) {
            // See completed in their department only
            all = requestRepository
                    .findByDepartmentAndStatusOrderByCreatedAtDesc(
                            currentUser.getDepartment(),
                            RequestStatus.COMPLETED
                    );
        } else {
            // Everyone else sees only their own completed requests
            all = requestRepository
                    .findByInitiatorAndStatusOrderByCreatedAtDesc(
                            currentUser,
                            RequestStatus.COMPLETED
                    );
        }

        return all.stream()
                .filter(r -> from == null ||
                        !r.getCreatedAt().isBefore(from))
                .filter(r -> to == null ||
                        !r.getCreatedAt().isAfter(to))
                .map(requestService::toDTO)
                .collect(Collectors.toList());
    }

    // ─── Rejected / Cancelled Cases ───────────────────────────

    public List<RequestResponseDto> getRejectedCases(
            User currentUser,
            LocalDateTime from,
            LocalDateTime to) {

        List<Request> all;

        if (currentUser.getRole() == Role.ADMIN ||
                currentUser.getRole() == Role.AUDITOR) {
            all = requestRepository.findByStatusOrderByCreatedAtDesc(
                    RequestStatus.REJECTED
            );
        } else if (currentUser.getRole() == Role.DEPARTMENT_HEAD) {
            all = requestRepository
                    .findByDepartmentAndStatusOrderByCreatedAtDesc(
                            currentUser.getDepartment(),
                            RequestStatus.REJECTED
                    );
        } else {
            all = requestRepository
                    .findByInitiatorAndStatusOrderByCreatedAtDesc(
                            currentUser,
                            RequestStatus.REJECTED
                    );
        }

        return all.stream()
                .filter(r -> from == null ||
                        !r.getCreatedAt().isBefore(from))
                .filter(r -> to == null ||
                        !r.getCreatedAt().isAfter(to))
                .map(requestService::toDTO)
                .collect(Collectors.toList());
    }

    // ─── Overdue Cases ────────────────────────────────────────

    public List<RequestResponseDto> getOverdueCases(User currentUser) {
        return requestRepository
                .findOverdueRequests(
                        LocalDateTime.now(),
                        List.of(RequestStatus.COMPLETED, RequestStatus.REJECTED)
                )
                .stream()
                .filter(r -> {
                    // Scope by role
                    if (currentUser.getRole() == Role.ADMIN ||
                            currentUser.getRole() == Role.AUDITOR) return true;
                    if (currentUser.getRole() == Role.DEPARTMENT_HEAD) {
                        return r.getDepartment().getId()
                                .equals(currentUser.getDepartment().getId());
                    }
                    return r.getInitiator().getId()
                            .equals(currentUser.getId());
                })
                .map(requestService::toDTO)
                .collect(Collectors.toList());
    }
}