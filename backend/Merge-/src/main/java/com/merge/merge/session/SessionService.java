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
    // Action path
    // -------------------------------------------------------------------------

    /**
     * Appends one action to an open session's path.
     *
     * <p>Side effects on first append:</p>
     * <ul>
     *   <li>{@code session.startedAt} is set — a session has no {@code startedAt} until
     *       its first PathEntry is recorded (creation and first action are two distinct moments).</li>
     * </ul>
     *
     * <p>On every append: {@code session.lastActivityAt} is updated to drive the idle sweep.</p>
     *
     * <p>{@code wasRequired} is server-computed: {@code true} iff this is the first time
     * this {@code actionType + conceptId} pair appears in the path, and the action type is
     * not {@link ActionType#CHAT_INTERACTION}.  Revisits and chat entries are always
     * {@code false}.</p>
     *
     * @throws SessionNotFoundException     if no session with the given id exists
     * @throws SessionAlreadyEndedException if the session is already closed
     */
    Session appendAction(UUID sessionId, AppendActionRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getEndedAt() != null) {
            throw new SessionAlreadyEndedException(sessionId);
        }

        Instant now = Instant.now();

        boolean wasRequired = computeWasRequired(session, request.actionType(), request.conceptId());

        PathEntry entry = PathEntry.builder()
                .actionType(request.actionType())
                .conceptId(request.conceptId())
                .timestamp(now)
                .result(request.result())
                .moodAtAction(request.moodAtAction())
                .wasRequired(wasRequired)
                .topicRelevance(request.topicRelevance())
                .inquiryDepth(request.inquiryDepth())
                .build();

        // Set startedAt on the very first action (PRD Section 3: set when first action recorded)
        if (session.getPath().isEmpty()) {
            session.setStartedAt(now);
        }

        session.getPath().add(entry);
        session.setLastActivityAt(now);

        return sessionRepository.save(session);
    }

    /**
     * Derives {@code wasRequired} for an incoming action.
     *
     * <p>Rule (PRD Section 4): {@code true} only for a concept's first required exposure —
     * i.e., the first occurrence of this {@code actionType + conceptId} pair in the path,
     * for action types that are gated by progression.  CHAT_INTERACTION is never required
     * (it uses {@code topicRelevance}/{@code inquiryDepth} instead).  Revisits of the same
     * {@code actionType + conceptId} are {@code false}.</p>
     */
    private static boolean computeWasRequired(Session session, ActionType actionType, UUID conceptId) {
        if (actionType == ActionType.CHAT_INTERACTION) {
            return false;
        }
        return session.getPath().stream()
                .noneMatch(e -> e.getActionType() == actionType
                        && conceptId.equals(e.getConceptId()));
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
