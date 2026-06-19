package com.asa.asaunify.dtos;



import com.asa.asaunify.enums.Role;
import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateUserRequest {

    // All fields optional — only provided fields are updated
    private String fullName;

    @Email(message = "Must be a valid email address")
    private String email;

    private Role role;

    private UUID departmentId;

    private Boolean isActive;
}
