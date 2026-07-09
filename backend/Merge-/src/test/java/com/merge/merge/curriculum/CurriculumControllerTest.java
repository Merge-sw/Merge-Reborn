package com.merge.merge.curriculum;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.curriculum.models.PredefinedContentRef;
import com.merge.merge.curriculum.service.ConceptService;
import com.merge.merge.curriculum.service.ResourceService;
import com.merge.merge.curriculum.service.StageService;
import com.merge.merge.identity.repository.StudentRepository;
import com.merge.merge.identity.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurriculumControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private AuthService authService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StageService stageService;
    @Autowired private ConceptService conceptService;
    @Autowired private ResourceService resourceService;
    @Autowired private StringRedisTemplate redisTemplate;

    private String token;
    private UUID stageId;
    private UUID conceptId;

    @BeforeEach
    void setUp() throws Exception {
        authService.register("curriculum@example.com", "correcthorse123", "Curriculum");
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"curriculum@example.com","password":"correcthorse123"}
                                """.strip()))
                .andReturn();
        token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        var stage = stageService.create("Foundations", 100);
        stageId = stage.getId();

        var concept = conceptService.create(stageId,
                new PredefinedContentRef("A system fails...", "Understand X", "Core content here"));
        conceptId = concept.getId();

        resourceService.create(conceptId, "VIDEO", "Intro to X", "https://example.com/video");
    }

    @AfterEach
    void cleanUp() {
        resourceService.listByConceptId(conceptId).forEach(r -> resourceService.delete(r.getId()));
        conceptService.delete(conceptId);
        stageService.delete(stageId);
        studentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // -------------------------------------------------------------------
    // GET /api/v1/stages
    // -------------------------------------------------------------------

    @Test
    void listStages_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/stages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listStages_validToken_returnsStageList() throws Exception {
        mockMvc.perform(get("/api/v1/stages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Foundations"))
                .andExpect(jsonPath("$[0].xpThreshold").value(100));
    }

    // -------------------------------------------------------------------
    // GET /api/v1/stages/{id}
    // -------------------------------------------------------------------

    @Test
    void getStage_unknownId_returns404WithProblemDetail() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/stages/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"title\"");
        assertThat(body).contains("\"detail\"");
        assertThat(body).doesNotContain("stackTrace");
    }

    @Test
    void getStage_knownId_returnsStageDto() throws Exception {
        mockMvc.perform(get("/api/v1/stages/" + stageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stageId.toString()))
                .andExpect(jsonPath("$.name").value("Foundations"));
    }

    // -------------------------------------------------------------------
    // GET /api/v1/concepts?stageId
    // -------------------------------------------------------------------

    @Test
    void listConcepts_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/concepts").param("stageId", stageId.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listConcepts_validStageId_returnsConceptList() throws Exception {
        mockMvc.perform(get("/api/v1/concepts")
                        .param("stageId", stageId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stageId").value(stageId.toString()));
    }

    @Test
    void listConcepts_unknownStageId_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/concepts")
                        .param("stageId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // -------------------------------------------------------------------
    // GET /api/v1/concepts/{id}
    // -------------------------------------------------------------------

    @Test
    void getConcept_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/concepts/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getConcept_knownId_returnsConceptDto() throws Exception {
        mockMvc.perform(get("/api/v1/concepts/" + conceptId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conceptId.toString()))
                .andExpect(jsonPath("$.stageId").value(stageId.toString()))
                .andExpect(jsonPath("$.predefinedContentRef.teachingObjective").value("Understand X"));
    }

    // -------------------------------------------------------------------
    // GET /api/v1/concepts/{id}/resources
    // -------------------------------------------------------------------

    @Test
    void listResources_unknownConceptId_returns404NotEmptyList() throws Exception {
        // Asking for resources of a non-existent concept returns 404, not [].
        // This distinguishes "concept exists but has no resources" from
        // "concept does not exist".
        mockMvc.perform(get("/api/v1/concepts/" + UUID.randomUUID() + "/resources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listResources_knownConcept_returnsResourceList() throws Exception {
        mockMvc.perform(get("/api/v1/concepts/" + conceptId + "/resources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Intro to X"))
                .andExpect(jsonPath("$[0].type").value("VIDEO"))
                .andExpect(jsonPath("$[0].url").value("https://example.com/video"));
    }

    @Test
    void listResources_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/concepts/" + conceptId + "/resources"))
                .andExpect(status().isUnauthorized());
    }
}
