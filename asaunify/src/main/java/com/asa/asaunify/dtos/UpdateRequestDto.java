package com.asa.asaunify.dtos;


import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateRequestDto {
    private String title;
    private String details;
    private String notes;
    private Map<String, Object> extraFields;
}
