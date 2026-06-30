package com.asa.asaunify.dtos;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class VehicleTripAssignmentDto {
    private UUID id;
    private UUID requestId;
    private String requestReferenceNumber;
    private String requestTitle;
    private String initiatorName;
    private String destination;
    private String tripDate;
    private String purpose;
    private String assignedByName;
    private LocalDateTime assignedAt;
    private LocalDateTime seenAt;
    private String note;
    private String requestStatus;
}