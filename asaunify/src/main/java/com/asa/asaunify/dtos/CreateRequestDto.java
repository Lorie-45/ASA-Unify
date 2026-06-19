package com.asa.asaunify.dtos;


import com.asa.asaunify.enums.RequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class CreateRequestDto {

    @NotNull(message = "Request type is required")
    private RequestType type;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Request details are required")
    private String details;

    // Optional — additional context
    private String notes;

    // Optional due date
    private LocalDateTime dueDate;

    private Map<String, Object> extraFields;

    // For loan top-ups only — links to the original loan request
    private UUID parentRequestId;
}