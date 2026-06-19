package com.asa.asaunify.dtos;


import com.asa.asaunify.enums.StageStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemoApprovalActionDto {

    @NotNull(message = "Action is required")
    private StageStatus action;

    private String comment;
}