package com.merge.merge.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodic sweep that closes sessions whose last recorded activity is older than
 * the idle threshold.  This is the backstop for exits nothing reports — closed
 * laptops, dead phones, crashes — that never fire an explicit end-session call.
 *
 * <p>The sweep runs on the same period as the idle threshold (5 minutes).  Running
 * more frequently provides no benefit; running less frequently means a session could
 * stay open up to 2× the threshold before being closed.</p>
 *
 * <p>This does not go through the deferred job queue.  It is a simple periodic in-process
 * sweep as specified in Section 2 of the Session Service PRD.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class IdleSessionSweeper {

    /**
     * Idle threshold: 5 minutes, expressed in milliseconds for {@code @Scheduled}.
     * Working default — expected to be revisited once real session data exists.
     * See PRD Section 2 for rationale.
     */
    static final long IDLE_THRESHOLD_MS = 5 * 60 * 1000L;

    private final SessionRepository sessionRepository;

    @Scheduled(fixedDelay = IDLE_THRESHOLD_MS)
    void closeIdleSessions() {
        Instant cutoff = Instant.now().minusMillis(IDLE_THRESHOLD_MS);
        List<Session> staleSessions = sessionRepository.findByEndedAtIsNullAndLastActivityAtBefore(cutoff);

        if (staleSessions.isEmpty()) {
            return;
        }

        log.info("Idle sweep: closing {} stale session(s) with lastActivityAt before {}", staleSessions.size(), cutoff);

        Instant now = Instant.now();
        for (Session session : staleSessions) {
            session.setEndedAt(now);
            session.setEndReason(EndReason.IDLE_TIMEOUT);
            sessionRepository.save(session);
            log.debug("Closed idle session {} for student {}", session.getId(), session.getStudentId());
        }
    }
}
