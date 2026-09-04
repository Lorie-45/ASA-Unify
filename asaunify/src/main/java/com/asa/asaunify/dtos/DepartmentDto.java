package com.asa.asaunify.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Department view for the API. The head is DERIVED from the single source of
 * truth — the active user whose department is this one and whose role is
 * DEPARTMENT_HEAD — never stored on the department itself.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class DepartmentDto {
    private UUID id;
    private String name;
    private UUID headUserId;   // derived, null if no active head
    private String headName;   // derived, null if no active head
}
