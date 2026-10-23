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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(readOnly = true)
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

    // Role-scoped listing: admins/auditors see everything, a department head
    // only their own department. Used by the export, which must never be wider
    // than what the same user can see in the on-screen reports.
    @Transactional(readOnly = true)
    public List<RequestResponseDto> getCasesForUser(
            User user,
            RequestStatus status,
            RequestType type,
            LocalDateTime from,
            LocalDateTime to) {

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.AUDITOR) {
            return getAllCases(status, type, from, to);
        }
        if (user.getRole() == Role.DEPARTMENT_HEAD) {
            return getDepartmentCases(user, status, type, from, to);
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "Access denied");
    }

    // ─── My Cases ─────────────────────────────────────────────

    // Any user — their own requests only
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<RequestResponseDto> getDepartmentCases(
            User deptHead,
            RequestStatus status,
            RequestType type,
            LocalDateTime from,
            LocalDateTime to) {

        if (deptHead.getDepartment() == null) {
            // Admins/auditors have no department and oversee everything.
            if (deptHead.getRole() == Role.ADMIN
                    || deptHead.getRole() == Role.AUDITOR) {
                return getAllCases(status, type, from, to);
            }
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<RequestResponseDto> getRejectedCases(
            User currentUser,
            LocalDateTime from,
            LocalDateTime to) {

        List<Request> all;

        if (currentUser.getRole() == Role.ADMIN ||
                currentUser.getRole() == Role.AUDITOR) {
            all = merge(
                    requestRepository.findByStatusOrderByCreatedAtDesc(
                            RequestStatus.REJECTED),
                    requestRepository.findByStatusOrderByCreatedAtDesc(
                            RequestStatus.CANCELLED));
        } else if (currentUser.getRole() == Role.DEPARTMENT_HEAD) {
            all = merge(
                    requestRepository
                            .findByDepartmentAndStatusOrderByCreatedAtDesc(
                                    currentUser.getDepartment(),
                                    RequestStatus.REJECTED),
                    requestRepository
                            .findByDepartmentAndStatusOrderByCreatedAtDesc(
                                    currentUser.getDepartment(),
                                    RequestStatus.CANCELLED));
        } else {
            all = merge(
                    requestRepository
                            .findByInitiatorAndStatusOrderByCreatedAtDesc(
                                    currentUser, RequestStatus.REJECTED),
                    requestRepository
                            .findByInitiatorAndStatusOrderByCreatedAtDesc(
                                    currentUser, RequestStatus.CANCELLED));
        }

        return all.stream()
                .filter(r -> from == null ||
                        !r.getCreatedAt().isBefore(from))
                .filter(r -> to == null ||
                        !r.getCreatedAt().isAfter(to))
                .map(requestService::toDTO)
                .collect(Collectors.toList());
    }

    // Rejected and cancelled both end a workflow without completing it, so the
    // "rejected / cancelled" report lists them together, newest first.
    private List<Request> merge(List<Request> a, List<Request> b) {
        List<Request> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        out.sort(java.util.Comparator.comparing(Request::getCreatedAt).reversed());
        return out;
    }

    // ─── Overdue Cases ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RequestResponseDto> getOverdueCases(User currentUser) {
        return requestRepository
                .findOverdueRequests(
                        LocalDateTime.now(),
                        List.of(RequestStatus.COMPLETED, RequestStatus.REJECTED,
                                RequestStatus.CANCELLED)
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