package com.merge.merge.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;

    // -------------------------------------------------------------------------
    // Session creation
    // -------------------------------------------------------------------------

    public Session getOrCreateOpenSession(UUID studentId, Mood initialMood) {
        Optional<Session> openSession = sessionRepository.findByStudentIdAndEndedAtIsNull(studentId);
        if (openSession.isPresent()) {
            return openSession.get();
        }

        Session newSession = Session.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .mood(initialMood)
                .type(deriveType(initialMood))
                .path(new ArrayList<>())
                .lastActivityAt(Instant.now())
                .build();

        try {
            return sessionRepository.save(newSession);
        } catch (DuplicateKeyException e) {
            log.info("Concurrent session creation detected for studentId: {}. Returning existing session.", studentId);
            return sessionRepository.findByStudentIdAndEndedAtIsNull(studentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Duplicate session creation detected, but could not find the existing open session.", e));
        }
    }

    // -------------------------------------------------------------------------
    // Session end
    // -------------------------------------------------------------------------

    /**
     * Ends an open session with the given reason.
     *
     * <p>This method is the single close path used by the idle sweep (IDLE_TIMEOUT),
     * the Build-passed event listener (COMPLETED), and the REST endpoint
     * (NAVIGATED_AWAY, EXHAUSTED).  Callers that face the client are responsible
     * for restricting which reasons they accept before calling here.</p>
     *
     * @throws SessionNotFoundException   if no session with the given id exists
     * @throws SessionAlreadyEndedException if the session is already closed
     */
    Session endSession(UUID sessionId, EndReason reason) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getEndedAt() != null) {
            throw new SessionAlreadyEndedException(sessionId);
        }

        session.setEndedAt(Instant.now());
        session.setEndReason(reason);
        return sessionRepository.save(session);
    }

    // -------------------------------------------------------------------------
    // Mood → type derivation (PRD Section 6)
    // -------------------------------------------------------------------------

    /**
     * Derives the session type from the student-reported mood.
     * FRESH or OKAY → FULL_FORCE.  EXHAUSTED → EXHAUSTED.
     * The client sets mood; the server derives type; the client never sets type directly.
     */
    static SessionType deriveType(Mood mood) {
        return switch (mood) {
            case FRESH, OKAY -> SessionType.FULL_FORCE;
            case EXHAUSTED -> SessionType.EXHAUSTED;
        };
    }
}
