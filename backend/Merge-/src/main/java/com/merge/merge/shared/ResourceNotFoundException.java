package com.merge.merge.shared;

/**
 * Thrown when a requested resource does not exist in the database.
 * Routed to HTTP 404 by GlobalExceptionHandler. All service methods that
 * look up by id or unique key must throw this instead of
 * NoSuchElementException so the handler can distinguish a missing resource
 * from a programming error.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException forId(String type, Object id) {
        return new ResourceNotFoundException("No " + type + " with id " + id);
    }
}
