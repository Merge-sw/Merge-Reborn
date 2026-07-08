package com.merge.merge.session;

import com.merge.merge.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void post_sessions_id_end_navigated_away_returns_200() throws Exception {
        Session session = savedOpenSession(Mood.FRESH);

        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NAVIGATED_AWAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endReason").value("NAVIGATED_AWAY"))
                .andExpect(jsonPath("$.endedAt").isNotEmpty());
    }

    @Test
    void post_sessions_id_end_exhausted_returns_200() throws Exception {
        Session session = savedOpenSession(Mood.EXHAUSTED);

        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"EXHAUSTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endReason").value("EXHAUSTED"))
                .andExpect(jsonPath("$.endedAt").isNotEmpty());
    }

    @Test
    void post_sessions_id_end_rejects_completed_reason_with_400() throws Exception {
        Session session = savedOpenSession(Mood.FRESH);

        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_sessions_id_end_rejects_idle_timeout_reason_with_400() throws Exception {
        Session session = savedOpenSession(Mood.FRESH);

        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"IDLE_TIMEOUT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_sessions_id_end_returns_404_for_unknown_id() throws Exception {
        mockMvc.perform(post("/sessions/{id}/end", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NAVIGATED_AWAY\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_sessions_id_end_returns_409_for_already_ended_session() throws Exception {
        Session session = savedOpenSession(Mood.FRESH);

        // Close once
        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NAVIGATED_AWAY\"}"))
                .andExpect(status().isOk());

        // Close again — should be 409
        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NAVIGATED_AWAY\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void post_sessions_id_end_persists_endedAt_in_database() throws Exception {
        Session session = savedOpenSession(Mood.OKAY);

        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NAVIGATED_AWAY\"}"))
                .andExpect(status().isOk());

        Session persisted = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(persisted.getEndedAt()).isNotNull();
        assertThat(persisted.getEndReason()).isEqualTo(EndReason.NAVIGATED_AWAY);
        // Session should no longer appear as open
        assertThat(sessionRepository.findByStudentIdAndEndedAtIsNull(session.getStudentId())).isEmpty();
    }

    private Session savedOpenSession(Mood mood) {
        Session s = Session.builder()
                .id(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .mood(mood)
                .type(SessionService.deriveType(mood))
                .lastActivityAt(Instant.now())
                .path(new ArrayList<>())
                .build();
        return sessionRepository.save(s);
    }
}
