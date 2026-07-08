package com.merge.merge.session;

import java.util.UUID;

class SessionNotFoundException extends RuntimeException {
    SessionNotFoundException(UUID sessionId) {
        super("Session not found: " + sessionId);
    }
}
