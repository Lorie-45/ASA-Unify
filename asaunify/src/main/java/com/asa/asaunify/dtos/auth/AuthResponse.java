package com.asa.asaunify.dtos.auth;

import com.asa.asaunify.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private UUID userId;
    private String fullName;
    private String email;
    private Role role;
    private String departmentName;
}