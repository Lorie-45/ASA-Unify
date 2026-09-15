package com.asa.asaunify.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyResetCodeRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 254, message = "Email is too long")
    private String email;

    // Shape is checked here so a malformed code never reaches the verifier
    // and never burns one of the account's limited attempts.
    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "^\\d{6}$",
            message = "Verification code must be 6 digits")
    private String code;
}
