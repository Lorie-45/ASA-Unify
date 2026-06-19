package com.asa.asaunify.dtos;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class NotificationSummaryDto {

    // Total unread count — powers the bell badge number
    private long unreadCount;

    // Most recent notifications — shown in the dropdown panel
    // Limited to last 10 by the service
    private List<NotificationDto> notifications;
}