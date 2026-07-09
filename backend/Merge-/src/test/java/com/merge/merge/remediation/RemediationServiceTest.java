package com.merge.merge.remediation;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.model.InstructorStatus;
import com.merge.merge.ai.repository.InstructorRepository;
import com.merge.merge.ai.service.InstructorService;
import com.merge.merge.identity.service.ContextService;
import com.merge.merge.integration.gemini.GeminiClient;
import com.merge.merge.remediation.models.Mission;
import com.merge.merge.remediation.repository.MissionRepository;
import com.merge.merge.remediation.service.RemediationService;
import com.merge.merge.build.event.BuildCompletedEvent;
import com.merge.merge.practice.event.DrillPassedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RemediationServiceTest {

    @Autowired
    private RemediationService remediationService;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private InstructorService instructorService;

    @Autowired
    private ContextService contextService;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        missionRepository.deleteAll();
        instructorRepository.deleteAll();
    }

    @Test
    void testFailureFlow_CreatesNewMission() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        contextService.createForStudent(studentId);

        // Mock Gemini response for failure flow
        String mockResponse = "[\n" +
                "  {\n" +
                "    \"painPointDescription\": \"Null reference exception\",\n" +
                "    \"matchedMissionId\": null,\n" +
                "    \"conceptAndContext\": \"Always initialize objects before usage.\"\n" +
                "  }\n" +
                "]";
        when(geminiClient.generate(anyString())).thenReturn(mockResponse);

        Map<String, Object> attemptData = new HashMap<>();
        attemptData.put("question", "What is 2+2?");
        attemptData.put("givenAnswer", "5");
        attemptData.put("correctAnswer", "4");
        attemptData.put("feedback", "Incorrect math calculation.");

        // Trigger failure flow
        remediationService.handleFailure(studentId, conceptId, "DRILL", attemptData);

        // Find queued job
        Instructor job = instructorRepository.findAll().stream()
                .filter(j -> j.getStatus() == InstructorStatus.QUEUED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No queued job found"));

        // Process job
        instructorService.processJob(job.getId());

        // Verify Mission is created
        List<Mission> missions = missionRepository.findByStudentIdAndConceptIdAndPassed(studentId, conceptId, false);
        assertThat(missions).hasSize(1);
        Mission mission = missions.get(0);
        assertThat(mission.getPainPointDescription()).isEqualTo("Null reference exception");
        assertThat(mission.getConceptAndContext()).isEqualTo("Always initialize objects before usage.");
        assertThat(mission.getAttemptHistory()).hasSize(1);
        assertThat(mission.getAttemptHistory().get(0).getAttemptData()).containsEntry("givenAnswer", "5");
        assertThat(mission.isPassed()).isFalse();
    }

    @Test
    void testResolutionFlow_ResolvesMissions() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        contextService.createForStudent(studentId);

        // Pre-create two open missions
        Mission mission1 = Mission.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .conceptId(conceptId)
                .painPointDescription("Point A")
                .conceptAndContext("Help A")
                .passed(false)
                .build();
        Mission mission2 = Mission.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .conceptId(conceptId)
                .painPointDescription("Point B")
                .conceptAndContext("Help B")
                .passed(false)
                .build();
        missionRepository.saveAll(List.of(mission1, mission2));

        // Mock Gemini response to resolve mission1 only
        String mockResponse = "{\n" +
                "  \"resolvedMissionIds\": [\"" + mission1.getId().toString() + "\"]\n" +
                "}";
        when(geminiClient.generate(anyString())).thenReturn(mockResponse);

        Map<String, Object> attemptData = new HashMap<>();
        attemptData.put("score", 100);

        // Trigger resolution flow
        remediationService.handlePass(studentId, conceptId, "DRILL", attemptData);

        // Find queued job
        Instructor job = instructorRepository.findAll().stream()
                .filter(j -> j.getStatus() == InstructorStatus.QUEUED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No queued job found"));

        // Process job
        instructorService.processJob(job.getId());

        // Verify mission1 is passed and mission2 remains open
        Mission updatedMission1 = missionRepository.findById(mission1.getId()).orElseThrow();
        Mission updatedMission2 = missionRepository.findById(mission2.getId()).orElseThrow();

        assertThat(updatedMission1.isPassed()).isTrue();
        assertThat(updatedMission2.isPassed()).isFalse();
    }

    @Test
    void testDrillPassedEvent_TriggersRemediationPass() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        UUID drillId = UUID.randomUUID();
        contextService.createForStudent(studentId);

        // Pre-create an open mission
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .conceptId(conceptId)
                .painPointDescription("Point X")
                .conceptAndContext("Help X")
                .passed(false)
                .build();
        missionRepository.save(mission);

        // Mock Gemini response to resolve the mission
        String mockResponse = "{\n" +
                "  \"resolvedMissionIds\": [\"" + mission.getId().toString() + "\"]\n" +
                "}";
        when(geminiClient.generate(anyString())).thenReturn(mockResponse);

        // Publish DrillPassedEvent
        DrillPassedEvent event = new DrillPassedEvent(this, studentId, conceptId, drillId);
        eventPublisher.publishEvent(event);

        // Find queued job
        Instructor job = instructorRepository.findAll().stream()
                .filter(j -> j.getStatus() == InstructorStatus.QUEUED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No queued job found"));

        // Process job
        instructorService.processJob(job.getId());

        // Verify mission is resolved
        Mission updated = missionRepository.findById(mission.getId()).orElseThrow();
        assertThat(updated.isPassed()).isTrue();
    }

    @Test
    void testBuildCompletedEvent_TriggersRemediationFailure() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        contextService.createForStudent(studentId);

        // Mock Gemini response for failure flow
        String mockResponse = "[\n" +
                "  {\n" +
                "    \"painPointDescription\": \"Build error\",\n" +
                "    \"matchedMissionId\": null,\n" +
                "    \"conceptAndContext\": \"Fix the failing build\"\n" +
                "  }\n" +
                "]";
        when(geminiClient.generate(anyString())).thenReturn(mockResponse);

        // Publish BuildCompletedEvent with passed=false
        BuildCompletedEvent event = new BuildCompletedEvent(this, studentId, conceptId, false, false, "key-123");
        eventPublisher.publishEvent(event);

        // Find queued job
        Instructor job = instructorRepository.findAll().stream()
                .filter(j -> j.getStatus() == InstructorStatus.QUEUED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No queued job found"));

        // Process job
        instructorService.processJob(job.getId());

        // Verify Mission is created
        List<Mission> missions = missionRepository.findByStudentIdAndConceptIdAndPassed(studentId, conceptId, false);
        assertThat(missions).hasSize(1);
        assertThat(missions.get(0).getPainPointDescription()).isEqualTo("Build error");
    }
}
