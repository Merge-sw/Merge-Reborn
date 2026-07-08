package com.merge.merge.session;

import com.merge.merge.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SessionServiceTests {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void testCreateSession_Success() {
        UUID studentId = UUID.randomUUID();
        Session session = sessionService.getOrCreateOpenSession(studentId, Mood.FRESH);

        assertThat(session).isNotNull();
        assertThat(session.getStudentId()).isEqualTo(studentId);
        assertThat(session.getMood()).isEqualTo(Mood.FRESH);
        assertThat(session.getType()).isEqualTo(SessionType.FULL_FORCE);
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.getPath()).isEmpty();
        // startedAt is null until the first PathEntry is appended, not set at creation.
        assertThat(session.getStartedAt()).isNull();

        // Query repository to verify
        Optional<Session> retrieved = sessionRepository.findByStudentIdAndEndedAtIsNull(studentId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(session.getId());
    }

    @Test
    void testGetOrCreateOpenSession_ReturnsExisting_IfOpen() {
        UUID studentId = UUID.randomUUID();
        Session firstSession = sessionService.getOrCreateOpenSession(studentId, Mood.FRESH);
        Session secondSession = sessionService.getOrCreateOpenSession(studentId, Mood.OKAY);

        // Should return the first session because it's still open
        assertThat(secondSession.getId()).isEqualTo(firstSession.getId());
        assertThat(secondSession.getMood()).isEqualTo(Mood.FRESH); // original mood remains
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetOrCreateOpenSession_HandlesRaceCondition() throws InterruptedException {
        UUID studentId = UUID.randomUUID();
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Session>[] results = new AtomicReference[threadCount];
        for (int i = 0; i < threadCount; i++) {
            results[i] = new AtomicReference<>();
        }

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Session session = sessionService.getOrCreateOpenSession(studentId, Mood.FRESH);
                    results[index].set(session);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount);

        // Verify that all threads got the exact same Session object/ID
        UUID commonSessionId = results[0].get().getId();
        for (int i = 0; i < threadCount; i++) {
            assertThat(results[i].get()).isNotNull();
            assertThat(results[i].get().getId()).isEqualTo(commonSessionId);
        }

        // Verify there is only one document in the DB
        long countInDb = sessionRepository.count();
        assertThat(countInDb).isEqualTo(1);
    }
}
