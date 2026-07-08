package com.merge.merge.session;

import com.merge.merge.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
class SessionActionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void post_actions_resource_view_returns_200_with_path_entry() throws Exception {
        Session session = savedOpenSession();
        UUID conceptId = UUID.randomUUID();

        mockMvc.perform(post("/sessions/{id}/actions", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType":"RESOURCE_VIEW","conceptId":"%s","moodAtAction":"FRESH"}
                                """.formatted(conceptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path[0].actionType").value("RESOURCE_VIEW"))
                .andExpect(jsonPath("$.path[0].wasRequired").value(true))
                .andExpect(jsonPath("$.path[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
    }

    @Test
    void post_actions_drill_attempt_returns_result_field() throws Exception {
        Session session = savedOpenSession();
        UUID conceptId = UUID.randomUUID();

        mockMvc.perform(post("/sessions/{id}/actions", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType":"DRILL_ATTEMPT","conceptId":"%s","moodAtAction":"OKAY","result":"FAILED"}
                                """.formatted(conceptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path[0].result").value("FAILED"))
                .andExpect(jsonPath("$.path[0].wasRequired").value(true));
    }

    @Test
    void post_actions_chat_interaction_wasRequired_is_false() throws Exception {
        Session session = savedOpenSession();
        UUID conceptId = UUID.randomUUID();

        mockMvc.perform(post("/sessions/{id}/actions", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType":"CHAT_INTERACTION","conceptId":"%s","moodAtAction":"FRESH",
                                 "topicRelevance":"ON_CONCEPT","inquiryDepth":"SURFACE"}
                                """.formatted(conceptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path[0].wasRequired").value(false))
                .andExpect(jsonPath("$.path[0].topicRelevance").value("ON_CONCEPT"))
                .andExpect(jsonPath("$.path[0].inquiryDepth").value("SURFACE"));
    }

    @Test
    void post_actions_returns_404_for_unknown_session() throws Exception {
        mockMvc.perform(post("/sessions/{id}/actions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType":"RESOURCE_VIEW","conceptId":"%s","moodAtAction":"FRESH"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_actions_returns_409_for_ended_session() throws Exception {
        Session session = savedOpenSession();
        // End the session first
        mockMvc.perform(post("/sessions/{id}/end", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NAVIGATED_AWAY\"}"))
                .andExpect(status().isOk());

        // Then try to append — should be 409
        mockMvc.perform(post("/sessions/{id}/actions", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType":"RESOURCE_VIEW","conceptId":"%s","moodAtAction":"FRESH"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void post_actions_persists_entry_in_database() throws Exception {
        Session session = savedOpenSession();
        UUID conceptId = UUID.randomUUID();

        mockMvc.perform(post("/sessions/{id}/actions", session.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType":"RESOURCE_VIEW","conceptId":"%s","moodAtAction":"FRESH"}
                                """.formatted(conceptId)))
                .andExpect(status().isOk());

        Session persisted = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(persisted.getPath()).hasSize(1);
        assertThat(persisted.getPath().get(0).getActionType()).isEqualTo(ActionType.RESOURCE_VIEW);
        assertThat(persisted.getPath().get(0).getConceptId()).isEqualTo(conceptId);
        assertThat(persisted.getStartedAt()).isNotNull();
        assertThat(persisted.getLastActivityAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Session savedOpenSession() {
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
}
