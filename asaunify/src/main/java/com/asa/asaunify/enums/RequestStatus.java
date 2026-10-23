package com.asa.asaunify.enums;

public enum RequestStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    // Workflow stopped by an admin / department head; never resumes.
    CANCELLED
}
