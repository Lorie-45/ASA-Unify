package com.asa.asaunify.dtos;


import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AssignDriverDto {

    @NotNull(message = "Driver is required")
    private UUID driverId;

    private String note;
}