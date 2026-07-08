package com.merge.merge.session.service;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(UUID sessionId) {
        super("Session not found: " + sessionId);
    }
}
