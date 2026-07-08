package com.merge.merge.session.service;

import java.util.UUID;

public class SessionAlreadyEndedException extends RuntimeException {
    public SessionAlreadyEndedException(UUID sessionId) {
        super("Session already ended: " + sessionId);
    }
}
