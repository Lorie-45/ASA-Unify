package com.asa.asaunify.dtos;

import com.asa.asaunify.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Minimal, non-sensitive view of a user for pickers (approver / driver
 * selection). Deliberately omits email, activation status and timestamps
 * so the role-lookup endpoints, which any authenticated user may call,
 * cannot be used to enumerate staff PII.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class UserSummaryDto {
    private UUID id;
    private String fullName;
    private Role role;
    private UUID departmentId;
    private String departmentName;
}
