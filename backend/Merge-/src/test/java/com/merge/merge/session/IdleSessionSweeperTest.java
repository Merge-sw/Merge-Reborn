package com.merge.merge.session;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.session.infrastructure.IdleSessionSweeper;
import com.merge.merge.session.model.*;
import com.merge.merge.session.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class IdleSessionSweeperTest {

    @Autowired
    private IdleSessionSweeper sweeper;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void sweep_closes_session_whose_lastActivityAt_is_past_threshold() {
        Instant staleTime = Instant.now().minusSeconds(6 * 60);
        Session staleSession = Session.builder()
                .id(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .mood(Mood.FRESH)
                .type(SessionType.FULL_FORCE)
                .lastActivityAt(staleTime)
                .path(new ArrayList<>())
                .build();
        sessionRepository.save(staleSession);

        sweeper.closeIdleSessions();

        Optional<Session> result = sessionRepository.findById(staleSession.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getEndedAt()).isNotNull();
        assertThat(result.get().getEndReason()).isEqualTo(EndReason.IDLE_TIMEOUT);
    }

    @Test
    void sweep_does_not_close_session_with_recent_activity() {
        Instant recentTime = Instant.now().minusSeconds(2 * 60);
        Session activeSession = Session.builder()
                .id(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .mood(Mood.OKAY)
                .type(SessionType.FULL_FORCE)
                .lastActivityAt(recentTime)
                .path(new ArrayList<>())
                .build();
        sessionRepository.save(activeSession);

        sweeper.closeIdleSessions();

        Optional<Session> result = sessionRepository.findById(activeSession.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getEndedAt()).isNull();
        assertThat(result.get().getEndReason()).isNull();
    }

    @Test
    void sweep_does_not_close_already_ended_session() {
        Instant staleTime = Instant.now().minusSeconds(10 * 60);
        Session alreadyClosed = Session.builder()
                .id(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .mood(Mood.FRESH)
                .type(SessionType.FULL_FORCE)
                .lastActivityAt(staleTime)
                .endedAt(staleTime.plusSeconds(60))
                .endReason(EndReason.NAVIGATED_AWAY)
                .path(new ArrayList<>())
                .build();
        sessionRepository.save(alreadyClosed);

        sweeper.closeIdleSessions();

        Optional<Session> result = sessionRepository.findById(alreadyClosed.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getEndReason()).isEqualTo(EndReason.NAVIGATED_AWAY);
    }

    @Test
    void sweep_closes_only_stale_sessions_when_mix_of_active_and_stale() {
        Session staleSession = Session.builder()
                .id(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .mood(Mood.FRESH)
                .type(SessionType.FULL_FORCE)
                .lastActivityAt(Instant.now().minusSeconds(8 * 60))
                .path(new ArrayList<>())
                .build();

        Session activeSession = Session.builder()
                .id(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .mood(Mood.OKAY)
                .type(SessionType.FULL_FORCE)
                .lastActivityAt(Instant.now().minusSeconds(60))
                .path(new ArrayList<>())
                .build();

        sessionRepository.save(staleSession);
        sessionRepository.save(activeSession);

        sweeper.closeIdleSessions();

        assertThat(sessionRepository.findById(staleSession.getId()).get().getEndReason())
                .isEqualTo(EndReason.IDLE_TIMEOUT);
        assertThat(sessionRepository.findById(staleSession.getId()).get().getEndedAt()).isNotNull();

        assertThat(sessionRepository.findById(activeSession.getId()).get().getEndedAt()).isNull();
        assertThat(sessionRepository.findById(activeSession.getId()).get().getEndReason()).isNull();
    }
}
