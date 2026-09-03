package com.asa.asaunify.exceptions;

/**
 * Thrown when creating/updating a resource would violate a uniqueness
 * constraint (e.g. an email or department name already in use). Mapped to
 * HTTP 409 Conflict by GlobalExceptionHandler.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
