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
 * Service-layer tests for appendAction: path tracking, wasRequired derivation,
 * startedAt on first action, and lastActivityAt update.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SessionAppendActionTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // startedAt
    // -------------------------------------------------------------------------

    @Test
    void first_append_sets_startedAt_on_session() {
        Session session = openSession();
        assertThat(session.getStartedAt()).isNull();

        Session updated = sessionService.appendAction(session.getId(), resourceViewRequest(UUID.randomUUID(), Mood.FRESH));

        assertThat(updated.getStartedAt()).isNotNull();
    }

    @Test
    void second_append_does_not_change_startedAt() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        Session afterFirst = sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.FRESH));
        // MongoDB stores Instant at millisecond precision; truncate before comparing to avoid
        // a spurious mismatch between the in-memory nano-precision value and the DB round-trip.
        Instant startedAtMillis = afterFirst.getStartedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        Session afterSecond = sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.FRESH));
        assertThat(afterSecond.getStartedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(startedAtMillis);
    }

    // -------------------------------------------------------------------------
    // lastActivityAt
    // -------------------------------------------------------------------------

    @Test
    void append_updates_lastActivityAt() {
        Session session = openSession();
        Instant beforeAppend = session.getLastActivityAt();

        // Small sleep to ensure the clock advances measurably
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        Session updated = sessionService.appendAction(session.getId(), resourceViewRequest(UUID.randomUUID(), Mood.FRESH));

        assertThat(updated.getLastActivityAt()).isAfter(beforeAppend);
    }

    // -------------------------------------------------------------------------
    // path growth
    // -------------------------------------------------------------------------

    @Test
    void append_adds_entry_to_path() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        Session updated = sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.OKAY));

        assertThat(updated.getPath()).hasSize(1);
        PathEntry entry = updated.getPath().get(0);
        assertThat(entry.getActionType()).isEqualTo(ActionType.RESOURCE_VIEW);
        assertThat(entry.getConceptId()).isEqualTo(conceptId);
        assertThat(entry.getMoodAtAction()).isEqualTo(Mood.OKAY);
        assertThat(entry.getTimestamp()).isNotNull();
        assertThat(entry.getResult()).isNull();
    }

    @Test
    void multiple_appends_accumulate_in_order() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.FRESH));
        sessionService.appendAction(session.getId(), drillAttemptRequest(conceptId, Result.FAILED, Mood.FRESH));
        Session updated = sessionService.appendAction(session.getId(), drillAttemptRequest(conceptId, Result.PASSED, Mood.OKAY));

        assertThat(updated.getPath()).hasSize(3);
        assertThat(updated.getPath().get(0).getActionType()).isEqualTo(ActionType.RESOURCE_VIEW);
        assertThat(updated.getPath().get(1).getActionType()).isEqualTo(ActionType.DRILL_ATTEMPT);
        assertThat(updated.getPath().get(2).getActionType()).isEqualTo(ActionType.DRILL_ATTEMPT);
    }

    // -------------------------------------------------------------------------
    // wasRequired derivation
    // -------------------------------------------------------------------------

    @Test
    void first_resource_view_on_concept_is_required() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        Session updated = sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.FRESH));

        assertThat(updated.getPath().get(0).getWasRequired()).isTrue();
    }

    @Test
    void second_resource_view_on_same_concept_is_not_required() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.FRESH));
        Session updated = sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.FRESH));

        assertThat(updated.getPath().get(1).getWasRequired()).isFalse();
    }

    @Test
    void first_drill_attempt_on_concept_is_required() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        Session updated = sessionService.appendAction(session.getId(), drillAttemptRequest(conceptId, Result.FAILED, Mood.FRESH));

        assertThat(updated.getPath().get(0).getWasRequired()).isTrue();
    }

    @Test
    void second_drill_attempt_on_same_concept_is_not_required() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        sessionService.appendAction(session.getId(), drillAttemptRequest(conceptId, Result.FAILED, Mood.FRESH));
        Session updated = sessionService.appendAction(session.getId(), drillAttemptRequest(conceptId, Result.PASSED, Mood.FRESH));

        assertThat(updated.getPath().get(1).getWasRequired()).isFalse();
    }

    @Test
    void first_concept_build_attempt_on_concept_is_required() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        Session updated = sessionService.appendAction(session.getId(), conceptBuildRequest(conceptId, Result.FAILED, Mood.FRESH));

        assertThat(updated.getPath().get(0).getWasRequired()).isTrue();
    }

    @Test
    void resource_view_and_drill_attempt_on_same_concept_are_independently_required() {
        // First RESOURCE_VIEW → wasRequired=true
        // First DRILL_ATTEMPT on the same concept → also wasRequired=true (different actionType)
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        sessionService.appendAction(session.getId(), resourceViewRequest(conceptId, Mood.FRESH));
        Session updated = sessionService.appendAction(session.getId(), drillAttemptRequest(conceptId, Result.FAILED, Mood.FRESH));

        assertThat(updated.getPath().get(0).getWasRequired()).isTrue();
        assertThat(updated.getPath().get(1).getWasRequired()).isTrue();
    }

    @Test
    void resource_view_on_different_concept_is_also_required() {
        Session session = openSession();
        UUID conceptA = UUID.randomUUID();
        UUID conceptB = UUID.randomUUID();

        sessionService.appendAction(session.getId(), resourceViewRequest(conceptA, Mood.FRESH));
        Session updated = sessionService.appendAction(session.getId(), resourceViewRequest(conceptB, Mood.FRESH));

        assertThat(updated.getPath().get(0).getWasRequired()).isTrue();
        assertThat(updated.getPath().get(1).getWasRequired()).isTrue();
    }

    @Test
    void chat_interaction_is_never_required() {
        Session session = openSession();
        UUID conceptId = UUID.randomUUID();

        Session updated = sessionService.appendAction(session.getId(), chatInteractionRequest(conceptId));

        assertThat(updated.getPath().get(0).getWasRequired()).isFalse();
    }

    // -------------------------------------------------------------------------
    // chat_interaction fields
    // -------------------------------------------------------------------------

    @Test
    void chat_interaction_stores_topic_relevance_and_inquiry_depth() {
        Session session = openSession();

        AppendActionRequest req = new AppendActionRequest(
                ActionType.CHAT_INTERACTION,
                UUID.randomUUID(),
                Mood.FRESH,
                null,
                TopicRelevance.EXPLORATORY,
                InquiryDepth.FOUNDATIONAL
        );
        Session updated = sessionService.appendAction(session.getId(), req);

        PathEntry entry = updated.getPath().get(0);
        assertThat(entry.getTopicRelevance()).isEqualTo(TopicRelevance.EXPLORATORY);
        assertThat(entry.getInquiryDepth()).isEqualTo(InquiryDepth.FOUNDATIONAL);
        assertThat(entry.getResult()).isNull();
        assertThat(entry.getWasRequired()).isFalse();
    }

    // -------------------------------------------------------------------------
    // result field
    // -------------------------------------------------------------------------

    @Test
    void drill_attempt_stores_result() {
        Session session = openSession();
        Session updated = sessionService.appendAction(session.getId(),
                drillAttemptRequest(UUID.randomUUID(), Result.PASSED, Mood.OKAY));

        assertThat(updated.getPath().get(0).getResult()).isEqualTo(Result.PASSED);
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void append_throws_not_found_for_unknown_session() {
        AppendActionRequest req = resourceViewRequest(UUID.randomUUID(), Mood.FRESH);
        assertThatThrownBy(() -> sessionService.appendAction(UUID.randomUUID(), req))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void append_throws_already_ended_for_closed_session() {
        Session session = openSession();
        sessionService.endSession(session.getId(), EndReason.NAVIGATED_AWAY);

        AppendActionRequest req = resourceViewRequest(UUID.randomUUID(), Mood.FRESH);
        assertThatThrownBy(() -> sessionService.appendAction(session.getId(), req))
                .isInstanceOf(SessionAlreadyEndedException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Session openSession() {
        Session s = Session.builder()
                .id(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .mood(Mood.FRESH)
                .type(SessionType.FULL_FORCE)
                .lastActivityAt(Instant.now())
                .path(new ArrayList<>())
                .build();
        return sessionRepository.save(s);
    }

    private AppendActionRequest resourceViewRequest(UUID conceptId, Mood mood) {
        return new AppendActionRequest(ActionType.RESOURCE_VIEW, conceptId, mood, null, null, null);
    }

    private AppendActionRequest drillAttemptRequest(UUID conceptId, Result result, Mood mood) {
        return new AppendActionRequest(ActionType.DRILL_ATTEMPT, conceptId, mood, result, null, null);
    }

    private AppendActionRequest conceptBuildRequest(UUID conceptId, Result result, Mood mood) {
        return new AppendActionRequest(ActionType.CONCEPT_BUILD_ATTEMPT, conceptId, mood, result, null, null);
    }

    private AppendActionRequest chatInteractionRequest(UUID conceptId) {
        return new AppendActionRequest(
                ActionType.CHAT_INTERACTION, conceptId, Mood.FRESH, null,
                TopicRelevance.ON_CONCEPT, InquiryDepth.SURFACE);
    }
}
