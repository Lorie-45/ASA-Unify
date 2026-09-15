package com.asa.asaunify.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 254, message = "Email is too long")
    private String email;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "^\\d{6}$",
            message = "Verification code must be 6 digits")
    private String code;

    // Same policy as CreateUserRequest — 6-128 chars with upper, lower, digit
    // and symbol. Kept identical on purpose: a reset must not be a way to set
    // a weaker password than account creation allows.
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 128,
            message = "Password must be between 6 and 128 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "Password must include an uppercase letter, a lowercase "
                    + "letter, a number and a special character"
    )
    private String newPassword;
}
