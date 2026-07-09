package com.merge.merge.ai;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.model.InstructorActionType;
import com.merge.merge.ai.model.InstructorStatus;
import com.merge.merge.ai.repository.InstructorRepository;
import com.merge.merge.ai.service.InstructorService;
import com.merge.merge.build.event.BuildCompletedEvent;
import com.merge.merge.build.event.BuildSubmittedEvent;
import com.merge.merge.build.event.ConceptBuildUnlockedEvent;
import com.merge.merge.build.models.ConceptBuild;
import com.merge.merge.build.repository.ConceptBuildRepository;
import com.merge.merge.practice.event.DrillPassedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "gemini.api.key=mock")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class InstructorServiceTest {

    @Autowired
    private InstructorService instructorService;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private ConceptBuildRepository conceptBuildRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private static final String QUEUE_NAME = "instructor:job:queue";

    @BeforeEach
    void setUp() {
        instructorRepository.deleteAll();
        conceptBuildRepository.deleteAll();
        redisTemplate.delete(QUEUE_NAME);
    }

    @Test
    void testChatInteractionSync() {
        UUID studentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Instructor result = instructorService.chatInteraction(studentId, sessionId, "Hello AI");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(InstructorStatus.COMPLETED);
        assertThat(result.getActionType()).isEqualTo(InstructorActionType.CHAT_INTERACTION);
        assertThat(result.getResult()).contains("Mock Gemini response");
        assertThat(result.getStudentId()).isEqualTo(studentId);
        assertThat(result.getSessionId()).isEqualTo(sessionId);

        // Verify stored in DB
        Instructor stored = instructorRepository.findById(result.getId()).orElse(null);
        assertThat(stored).isNotNull();
        assertThat(stored.getResult()).isEqualTo(result.getResult());
    }



    @Test
    void testSyncComprehensionGenerateEvent() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        UUID drillId = UUID.randomUUID();

        DrillPassedEvent event = new DrillPassedEvent(this, studentId, conceptId, drillId);
        eventPublisher.publishEvent(event);

        assertThat(event.getResult()).isNotNull();
        assertThat(event.getResult()).contains("Mock Gemini response");
        assertThat(event.getInstructorId()).isNotNull();

        Instructor stored = instructorRepository.findById(event.getInstructorId()).orElse(null);
        assertThat(stored).isNotNull();
        assertThat(stored.getActionType()).isEqualTo(InstructorActionType.COMPREHENSION_GENERATE);
        assertThat(stored.getStatus()).isEqualTo(InstructorStatus.COMPLETED);
    }

    @Test
    void testAsyncBuildPrdGenerateEvent() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        ConceptBuildUnlockedEvent event = new ConceptBuildUnlockedEvent(this, studentId, conceptId, idempotencyKey);
        eventPublisher.publishEvent(event);

        // Verify job record created in QUEUED status
        Instructor record = instructorRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        assertThat(record).isNotNull();
        assertThat(record.getStatus()).isEqualTo(InstructorStatus.QUEUED);
        assertThat(record.getActionType()).isEqualTo(InstructorActionType.BUILD_PRD_GENERATE);

        // Verify task in Redis queue (or that it has already been popped and is being processed by the worker)
        String queuedTaskId = redisTemplate.opsForList().rightPop(QUEUE_NAME);
        if (queuedTaskId != null) {
            assertThat(queuedTaskId).isEqualTo(record.getId().toString());
            // Process job manually since we popped it
            instructorService.processJob(record.getId());
        } else {
            // If already popped, ensure the state is running or completed in DB
            Instructor current = instructorRepository.findById(record.getId()).orElseThrow();
            assertThat(current.getStatus()).isIn(InstructorStatus.RUNNING, InstructorStatus.COMPLETED);
        }

        Instructor processed = instructorRepository.findById(record.getId()).orElse(null);
        assertThat(processed).isNotNull();
        assertThat(processed.getStatus()).isEqualTo(InstructorStatus.COMPLETED);
        assertThat(processed.getResult()).contains("Product Requirement Document");
    }

    @Test
    void testAsyncCleanCodeReviewEvent() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        String code = "public class Solution {}";
        String idempotencyKey = UUID.randomUUID().toString();

        BuildSubmittedEvent event = new BuildSubmittedEvent(this, studentId, conceptId, code, idempotencyKey);
        eventPublisher.publishEvent(event);

        Instructor record = instructorRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        assertThat(record).isNotNull();
        assertThat(record.getStatus()).isEqualTo(InstructorStatus.QUEUED);
        assertThat(record.getActionType()).isEqualTo(InstructorActionType.CLEAN_CODE_REVIEW);
        assertThat(record.getContext().get("code")).isEqualTo(code);

        instructorService.processJob(record.getId());

        Instructor processed = instructorRepository.findById(record.getId()).orElse(null);
        assertThat(processed).isNotNull();
        assertThat(processed.getStatus()).isEqualTo(InstructorStatus.COMPLETED);
        assertThat(processed.getResult()).contains("clean code review");
    }

    @Test
    void testAsyncReflectEvent() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        BuildCompletedEvent event = new BuildCompletedEvent(this, studentId, conceptId, true, false, idempotencyKey);
        eventPublisher.publishEvent(event);

        Instructor record = instructorRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        assertThat(record).isNotNull();
        assertThat(record.getStatus()).isEqualTo(InstructorStatus.QUEUED);
        assertThat(record.getActionType()).isEqualTo(InstructorActionType.REFLECT);

        instructorService.processJob(record.getId());

        Instructor processed = instructorRepository.findById(record.getId()).orElse(null);
        assertThat(processed).isNotNull();
        assertThat(processed.getStatus()).isEqualTo(InstructorStatus.COMPLETED);
        assertThat(processed.getResult()).contains("personalized reflection prompt");
    }

    @Test
    void testSpecialAudioDecisionExhausted() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        // 1. ConceptBuild does not exist -> Passed is false -> AUDIO_REINFORCE
        Instructor job1 = instructorService.handleSessionExhausted(studentId, sessionId, conceptId);
        assertThat(job1.getActionType()).isEqualTo(InstructorActionType.AUDIO_REINFORCE);
        assertThat(job1.getStatus()).isEqualTo(InstructorStatus.QUEUED);

        // 2. ConceptBuild exists and passed is false -> AUDIO_REINFORCE
        conceptBuildRepository.save(new ConceptBuild(UUID.randomUUID(), studentId, conceptId, false));
        Instructor job2 = instructorService.handleSessionExhausted(studentId, sessionId, conceptId);
        // Note: AUDIO_REINFORCE is guarded, but since job1 was only QUEUED (not COMPLETED), it enqueues a new job
        assertThat(job2.getActionType()).isEqualTo(InstructorActionType.AUDIO_REINFORCE);
        assertThat(job2.getId()).isNotEqualTo(job1.getId());

        // 3. Mark job1 as COMPLETED, then check guard
        job1.setStatus(InstructorStatus.COMPLETED);
        instructorRepository.save(job1);

        Instructor job3 = instructorService.handleSessionExhausted(studentId, sessionId, conceptId);
        assertThat(job3.getId()).isEqualTo(job1.getId()); // Returns cached completed job!

        // 4. Update ConceptBuild to passed -> AUDIO_PRIME
        ConceptBuild cb = conceptBuildRepository.findByStudentIdAndConceptId(studentId, conceptId).orElseThrow();
        cb.setPassed(true);
        conceptBuildRepository.save(cb);

        Instructor job4 = instructorService.handleSessionExhausted(studentId, sessionId, conceptId);
        assertThat(job4.getActionType()).isEqualTo(InstructorActionType.AUDIO_PRIME);
        assertThat(job4.getStatus()).isEqualTo(InstructorStatus.QUEUED);
    }

    @Test
    void testIdempotencyGuards() {
        UUID studentId = UUID.randomUUID();
        UUID conceptId = UUID.randomUUID();

        // 1. Mission generate is not guarded
        Map<String, Object> ctx = new HashMap<>();
        Instructor m1 = instructorService.missionGenerate(studentId, conceptId, ctx);
        Instructor m2 = instructorService.missionGenerate(studentId, conceptId, ctx);
        assertThat(m1.getId()).isNotEqualTo(m2.getId());

        // 2. Audio prime is guarded
        Instructor p1 = instructorService.audioPrime(studentId, UUID.randomUUID(), conceptId);
        Instructor p2 = instructorService.audioPrime(studentId, UUID.randomUUID(), conceptId);
        assertThat(p1.getId()).isNotEqualTo(p2.getId()); // Different because p1 is QUEUED, not COMPLETED

        p1.setStatus(InstructorStatus.COMPLETED);
        instructorRepository.save(p1);

        Instructor p3 = instructorService.audioPrime(studentId, UUID.randomUUID(), conceptId);
        assertThat(p3.getId()).isEqualTo(p1.getId()); // Reuses COMPLETED job!
    }
}
