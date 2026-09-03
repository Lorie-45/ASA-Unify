package com.asa.asaunify.dtos;

import com.asa.asaunify.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    // Password policy: at least 12 chars, with upper, lower, digit and symbol.
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 128,
            message = "Password must be between 6 and 128 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "Password must include an uppercase letter, a lowercase "
                    + "letter, a number and a special character"
    )
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    // Department is required for all roles except ADMIN and AUDITOR
    private UUID departmentId;
}