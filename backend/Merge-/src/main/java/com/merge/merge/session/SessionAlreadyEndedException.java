package com.merge.merge.session;

import java.util.UUID;

class SessionAlreadyEndedException extends RuntimeException {
    SessionAlreadyEndedException(UUID sessionId) {
        super("Session already ended: " + sessionId);
    }
}
