package com.merge.merge.session;

import com.merge.merge.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-layer tests for all four endReason paths.
 *
 * COMPLETED and IDLE_TIMEOUT come from non-client paths (Build event, idle sweep).
 * NAVIGATED_AWAY and EXHAUSTED come from the client endpoint.
 * This layer tests the underlying endSession logic for all four.
 */
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SessionEndTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Mood → type derivation
    // -------------------------------------------------------------------------

    @Test
    void fresh_mood_derives_full_force_type() {
        assertThat(SessionService.deriveType(Mood.FRESH)).isEqualTo(SessionType.FULL_FORCE);
    }

    @Test
    void okay_mood_derives_full_force_type() {
        assertThat(SessionService.deriveType(Mood.OKAY)).isEqualTo(SessionType.FULL_FORCE);
    }

    @Test
    void exhausted_mood_derives_exhausted_type() {
        assertThat(SessionService.deriveType(Mood.EXHAUSTED)).isEqualTo(SessionType.EXHAUSTED);
    }

    // -------------------------------------------------------------------------
    // NAVIGATED_AWAY path
    // -------------------------------------------------------------------------

    @Test
    void end_session_navigated_away_closes_open_session() {
        Session session = openSession(UUID.randomUUID(), Mood.FRESH);

        Session ended = sessionService.endSession(session.getId(), EndReason.NAVIGATED_AWAY);

        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(ended.getEndReason()).isEqualTo(EndReason.NAVIGATED_AWAY);
    }

    // -------------------------------------------------------------------------
    // EXHAUSTED path
    // -------------------------------------------------------------------------

    @Test
    void end_session_exhausted_closes_open_session() {
        Session session = openSession(UUID.randomUUID(), Mood.EXHAUSTED);

        Session ended = sessionService.endSession(session.getId(), EndReason.EXHAUSTED);

        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(ended.getEndReason()).isEqualTo(EndReason.EXHAUSTED);
    }

    // -------------------------------------------------------------------------
    // COMPLETED path (called by Build-passed event, not the client endpoint)
    // -------------------------------------------------------------------------

    @Test
    void end_session_completed_closes_open_session() {
        Session session = openSession(UUID.randomUUID(), Mood.FRESH);

        Session ended = sessionService.endSession(session.getId(), EndReason.COMPLETED);

        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(ended.getEndReason()).isEqualTo(EndReason.COMPLETED);
    }

    // -------------------------------------------------------------------------
    // IDLE_TIMEOUT path (called by the idle sweep, not the client endpoint)
    // -------------------------------------------------------------------------

    @Test
    void end_session_idle_timeout_closes_open_session() {
        Session session = openSession(UUID.randomUUID(), Mood.OKAY);

        Session ended = sessionService.endSession(session.getId(), EndReason.IDLE_TIMEOUT);

        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(ended.getEndReason()).isEqualTo(EndReason.IDLE_TIMEOUT);
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void end_session_throws_not_found_for_unknown_session_id() {
        assertThatThrownBy(() -> sessionService.endSession(UUID.randomUUID(), EndReason.NAVIGATED_AWAY))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void end_session_throws_already_ended_for_closed_session() {
        Session session = openSession(UUID.randomUUID(), Mood.FRESH);
        // Close it once
        sessionService.endSession(session.getId(), EndReason.NAVIGATED_AWAY);
        // Try to close again
        assertThatThrownBy(() -> sessionService.endSession(session.getId(), EndReason.NAVIGATED_AWAY))
                .isInstanceOf(SessionAlreadyEndedException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Session openSession(UUID studentId, Mood mood) {
        Session s = Session.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .mood(mood)
                .type(SessionService.deriveType(mood))
                .lastActivityAt(Instant.now())
                .path(new ArrayList<>())
                .build();
        return sessionRepository.save(s);
    }
}
