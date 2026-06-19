package com.asa.asaunify.dtos;


import com.asa.asaunify.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class UserDto {

    private UUID id;
    private String fullName;
    private String email;
    private Role role;
    private UUID departmentId;
    private String departmentName;
    private boolean isActive;
    private LocalDateTime createdAt;
}