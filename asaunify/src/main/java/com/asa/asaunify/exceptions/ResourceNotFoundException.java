package com.asa.asaunify.exceptions;

/**
 * Thrown when a requested resource does not exist (or the caller is not
 * allowed to see it — the message is deliberately identical in both cases so
 * resource ids cannot be probed). Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
