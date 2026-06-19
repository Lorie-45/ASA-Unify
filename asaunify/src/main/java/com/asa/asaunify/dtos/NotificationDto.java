package com.asa.asaunify.dtos;

import com.asa.asaunify.enums.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class NotificationDto {

    private UUID id;
    private String title;
    private String body;
    private ActionType actionType;
    private boolean isRead;
    private LocalDateTime createdAt;

    // What this notification links to
    // One of these will be non-null, the other null
    private UUID requestId;
    private String requestReferenceNumber;
    private UUID memoId;
    private String memoReferenceNumber;
}