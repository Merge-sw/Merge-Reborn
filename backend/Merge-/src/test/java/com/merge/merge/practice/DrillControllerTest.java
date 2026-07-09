package com.merge.merge.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.model.InstructorActionType;
import com.merge.merge.ai.model.InstructorStatus;
import com.merge.merge.ai.service.InstructorService;
import com.merge.merge.curriculum.models.Concept;
import com.merge.merge.curriculum.models.PredefinedContentRef;
import com.merge.merge.curriculum.repository.ConceptRepository;
import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.repository.StudentRepository;
import com.merge.merge.identity.service.AuthService;
import com.merge.merge.practice.dto.CreateDrillRequest;
import com.merge.merge.practice.dto.SubmitDrillRequest;
import com.merge.merge.practice.model.Drill;
import com.merge.merge.practice.model.SubmissionStatus;
import com.merge.merge.practice.repository.DrillRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DrillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DrillRepository drillRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private InstructorService instructorService;

    @MockitoBean
    private MissionTrigger missionTrigger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID conceptId;
    private String accessToken;
    private UUID studentId;

    @BeforeEach
    void setUp() throws Exception {
        drillRepository.deleteAll();
        conceptRepository.deleteAll();
        studentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        // Save a mock concept
        Concept concept = new Concept(UUID.randomUUID(), new PredefinedContentRef("scenario", "objective", "content"));
        conceptRepository.save(concept);
        conceptId = concept.getId();

        // Register and login a student
        authService.register("student@example.com", "securePassword123", "Alice Student");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "student@example.com",
                                  "password": "securePassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        Student student = studentRepository.findByEmail("student@example.com").orElseThrow();
        studentId = student.getId();

        Instructor mockComprehension = Instructor.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .conceptId(conceptId)
                .actionType(InstructorActionType.COMPREHENSION_GENERATE)
                .status(InstructorStatus.COMPLETED)
                .result("Mock comprehension feedback")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(instructorService.generateComprehensionSync(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(mockComprehension);
    }

    @AfterEach
    void cleanUp() {
        drillRepository.deleteAll();
        conceptRepository.deleteAll();
        studentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void testSuccessfulDrillCreationAndSubmissionFlow() throws Exception {
        // 1. Stub InstructorService
        Instructor mockInstructor = Instructor.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .conceptId(conceptId)
                .actionType(InstructorActionType.DRILL_GENERATE)
                .status(InstructorStatus.COMPLETED)
                .result("QUESTION: What is the complexity of binary search?\nANSWER: O(log n)")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(instructorService.generateDrillSync(eq(studentId), eq(conceptId)))
                .thenReturn(mockInstructor);

        // 2. POST /api/v1/drills to create a Drill
        CreateDrillRequest createRequest = new CreateDrillRequest(conceptId);
        MvcResult createResult = mockMvc.perform(post("/api/v1/drills")
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.question").value("What is the complexity of binary search?"))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.xpAwarded").value(0))
                .andExpect(jsonPath("$.status").value("PENDING"))
                // Expect answer NOT to leak in response
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andReturn();

        UUID drillId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText());

        // 3. POST /api/v1/drills/{id}/submit to submit correct answer
        SubmitDrillRequest submitRequest = new SubmitDrillRequest(
                "O(log n)",
                "idempotency-key-1",
                true,
                2
        );

        mockMvc.perform(post("/api/v1/drills/{id}/submit", drillId)
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.xpAwarded").value(10))
                .andExpect(jsonPath("$.pasteAttempted").value(true))
                .andExpect(jsonPath("$.tabFocusLost").value(2));

        // Verify Drill is updated in DB
        Drill drill = drillRepository.findById(drillId).orElseThrow();
        assertThat(drill.isPassed()).isTrue();
        assertThat(drill.getXpAwarded()).isEqualTo(10);
        assertThat(drill.getStatus()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(drill.isPasteAttempted()).isTrue();
        assertThat(drill.getTabFocusLost()).isEqualTo(2);

        // Verify student's XP is incremented (starts at 0, should be 10 now)
        Student student = studentRepository.findById(studentId).orElseThrow();
        assertThat(student.getXp()).isEqualTo(10);
    }

    @Test
    void testLateSubmissionAutoFails() throws Exception {
        // Create a Drill in the DB that is already expired
        Drill lateDrill = Drill.builder()
                .id(UUID.randomUUID())
                .conceptId(conceptId)
                .studentId(studentId)
                .question("What is 1 + 1?")
                .answer("2")
                .passed(false)
                .xpAwarded(0)
                .status(SubmissionStatus.PENDING)
                .serverDeadline(Instant.now().minusSeconds(5)) // Expired 5 seconds ago
                .createdAt(Instant.now().minusSeconds(15))
                .build();
        drillRepository.save(lateDrill);

        SubmitDrillRequest submitRequest = new SubmitDrillRequest(
                "2", // Correct answer, but late
                "idempotency-key-late",
                false,
                0
        );

        mockMvc.perform(post("/api/v1/drills/{id}/submit", lateDrill.getId())
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.feedback").value("Submission arrived after the server deadline."))
                .andExpect(jsonPath("$.xpAwarded").value(0));

        Drill drill = drillRepository.findById(lateDrill.getId()).orElseThrow();
        assertThat(drill.isPassed()).isFalse();
        assertThat(drill.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(drill.getFeedback()).isEqualTo("Submission arrived after the server deadline.");

        // Verify MissionTrigger was called on failure
        verify(missionTrigger).trigger(eq(studentId), eq(conceptId));
    }

    @Test
    void testFailedSubmissionTriggersMissionTrigger() throws Exception {
        Drill drill = Drill.builder()
                .id(UUID.randomUUID())
                .conceptId(conceptId)
                .studentId(studentId)
                .question("What is 1 + 1?")
                .answer("2")
                .passed(false)
                .xpAwarded(0)
                .status(SubmissionStatus.PENDING)
                .serverDeadline(Instant.now().plusSeconds(60))
                .createdAt(Instant.now())
                .build();
        drillRepository.save(drill);

        SubmitDrillRequest submitRequest = new SubmitDrillRequest(
                "wrong answer",
                "idempotency-key-fail",
                false,
                1
        );

        mockMvc.perform(post("/api/v1/drills/{id}/submit", drill.getId())
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.feedback").value("Answer did not match."))
                .andExpect(jsonPath("$.xpAwarded").value(0))
                .andExpect(jsonPath("$.tabFocusLost").value(1));

        // Verify MissionTrigger was called on failure
        verify(missionTrigger).trigger(eq(studentId), eq(conceptId));
    }

    @Test
    void testNoOpMissionTriggerLogsCorrectly() {
        NoOpMissionTrigger noOpMissionTrigger = new NoOpMissionTrigger();
        // Simply ensure trigger executes without error
        noOpMissionTrigger.trigger(studentId, conceptId);
    }

    @Test
    void testIdempotencyKeyPreventsDuplicateSubmission() throws Exception {
        Drill drill = Drill.builder()
                .id(UUID.randomUUID())
                .conceptId(conceptId)
                .studentId(studentId)
                .question("What is 1 + 1?")
                .answer("2")
                .passed(false)
                .xpAwarded(0)
                .status(SubmissionStatus.PENDING)
                .serverDeadline(Instant.now().plusSeconds(60))
                .createdAt(Instant.now())
                .build();
        drillRepository.save(drill);

        SubmitDrillRequest submitRequest = new SubmitDrillRequest(
                "2",
                "idempotency-key-duplicate",
                false,
                0
        );

        // Submit first time
        mockMvc.perform(post("/api/v1/drills/{id}/submit", drill.getId())
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        // Submit second time with same idempotency key — should return successfully immediately without re-awarding XP
        mockMvc.perform(post("/api/v1/drills/{id}/submit", drill.getId())
                        .headers(authHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        // Verify student's XP is 10 (awarded exactly once)
        Student student = studentRepository.findById(studentId).orElseThrow();
        assertThat(student.getXp()).isEqualTo(10);
    }
}
