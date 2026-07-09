package com.merge.merge.ai.service;

import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.model.InstructorActionType;
import com.merge.merge.ai.model.InstructorStatus;
import com.merge.merge.ai.repository.InstructorRepository;
import com.merge.merge.build.models.ConceptBuild;
import com.merge.merge.build.repository.ConceptBuildRepository;
import com.merge.merge.ai.event.InstructorJobCompletedEvent;
import com.merge.merge.integration.gemini.GeminiClient;
import com.merge.merge.shared.queue.RedisTaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstructorServiceImpl implements InstructorService {

    private final InstructorRepository instructorRepository;
    private final GeminiClient geminiClient;
    private final RedisTaskQueue redisTaskQueue;
    private final ConceptBuildRepository conceptBuildRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final String QUEUE_NAME = "instructor:job:queue";

    // -------------------------------------------------------------------------
    // Direct-call APIs (Sync and Async)
    // -------------------------------------------------------------------------

    @Override
    public Instructor chatInteraction(UUID studentId, UUID sessionId, String message) {
        log.info("Processing CHAT_INTERACTION synchronously for student: {}, session: {}", studentId, sessionId);

        String prompt = String.format("Respond to the student's message in a chat context. Student: %s, Message: %s", studentId, message);
        String result = geminiClient.generate(prompt);

        Instructor instructor = Instructor.builder()
                .id(UUID.randomUUID())
                .actionType(InstructorActionType.CHAT_INTERACTION)
                .status(InstructorStatus.COMPLETED)
                .studentId(studentId)
                .sessionId(sessionId)
                .result(result)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return instructorRepository.save(instructor);
    }

    @Override
    public Instructor handleSessionExhausted(UUID studentId, UUID sessionId, UUID conceptId) {
        log.info("Session exhausted for student: {}, concept: {}. Deciding between prime and reinforce.", studentId, conceptId);

        // Check if concept build is passed
        boolean passed = conceptBuildRepository.findByStudentIdAndConceptId(studentId, conceptId)
                .map(ConceptBuild::isPassed)
                .orElse(false);

        if (passed) {
            log.info("Concept build passed. Dispatching AUDIO_PRIME.");
            return audioPrime(studentId, sessionId, conceptId);
        } else {
            log.info("Concept build not yet passed. Dispatching AUDIO_REINFORCE.");
            return audioReinforce(studentId, sessionId, conceptId);
        }
    }

    @Override
    public Instructor audioReinforce(UUID studentId, UUID sessionId, UUID conceptId) {
        // Guarded: check for existing successful generation
        List<Instructor> existing = instructorRepository.findByActionTypeAndStudentIdAndConceptIdAndStatus(
                InstructorActionType.AUDIO_REINFORCE, studentId, conceptId, InstructorStatus.COMPLETED
        );
        if (!existing.isEmpty()) {
            log.info("AUDIO_REINFORCE already completed for student: {} and concept: {}. Returning cached result.", studentId, conceptId);
            return existing.get(0);
        }

        return enqueueJob(InstructorActionType.AUDIO_REINFORCE, studentId, conceptId, sessionId, null, null);
    }

    @Override
    public Instructor audioPrime(UUID studentId, UUID sessionId, UUID conceptId) {
        // Guarded: check for existing successful generation
        List<Instructor> existing = instructorRepository.findByActionTypeAndStudentIdAndConceptIdAndStatus(
                InstructorActionType.AUDIO_PRIME, studentId, conceptId, InstructorStatus.COMPLETED
        );
        if (!existing.isEmpty()) {
            log.info("AUDIO_PRIME already completed for student: {} and concept: {}. Returning cached result.", studentId, conceptId);
            return existing.get(0);
        }

        return enqueueJob(InstructorActionType.AUDIO_PRIME, studentId, conceptId, sessionId, null, null);
    }

    @Override
    public Instructor missionGenerate(UUID studentId, UUID conceptId, Map<String, Object> context) {
        // Not guarded: duplicate generation is harmless
        return enqueueJob(InstructorActionType.MISSION_GENERATE, studentId, conceptId, null, context, null);
    }

    // -------------------------------------------------------------------------
    // Event-reaction APIs (Sync and Async)
    // -------------------------------------------------------------------------

    @Override
    public Instructor generateDrillSync(UUID studentId, UUID conceptId) {
        log.info("Generating DRILL_GENERATE synchronously for student: {}, concept: {}", studentId, conceptId);

        String prompt = String.format("Generate a drill for student %s focusing on concept %s.", studentId, conceptId);
        String result = geminiClient.generate(prompt);

        Instructor instructor = Instructor.builder()
                .id(UUID.randomUUID())
                .actionType(InstructorActionType.DRILL_GENERATE)
                .status(InstructorStatus.COMPLETED)
                .studentId(studentId)
                .conceptId(conceptId)
                .result(result)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return instructorRepository.save(instructor);
    }

    @Override
    public Instructor generateComprehensionSync(UUID studentId, UUID conceptId, UUID drillId) {
        log.info("Generating COMPREHENSION_GENERATE synchronously for student: {}, concept: {}, drill: {}", studentId, conceptId, drillId);

        String prompt = String.format("Generate a comprehension check for student %s on concept %s after passing drill %s.", studentId, conceptId, drillId);
        String result = geminiClient.generate(prompt);

        Instructor instructor = Instructor.builder()
                .id(UUID.randomUUID())
                .actionType(InstructorActionType.COMPREHENSION_GENERATE)
                .status(InstructorStatus.COMPLETED)
                .studentId(studentId)
                .conceptId(conceptId)
                .result(result)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return instructorRepository.save(instructor);
    }

    @Override
    public Instructor generateBuildPrdAsync(UUID studentId, UUID conceptId, String idempotencyKey) {
        // Rides on existing build idempotencyKey
        if (idempotencyKey != null) {
            var existing = instructorRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("PRD Generation already exists for idempotencyKey: {}. Returning it.", idempotencyKey);
                return existing.get();
            }
        }

        return enqueueJob(InstructorActionType.BUILD_PRD_GENERATE, studentId, conceptId, null, null, idempotencyKey);
    }

    @Override
    public Instructor generateCleanCodeReviewAsync(UUID studentId, UUID conceptId, String code, String idempotencyKey) {
        // Rides on existing build idempotencyKey
        if (idempotencyKey != null) {
            var existing = instructorRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Clean Code Review already exists for idempotencyKey: {}. Returning it.", idempotencyKey);
                return existing.get();
            }
        }

        Map<String, Object> context = new HashMap<>();
        context.put("code", code);

        return enqueueJob(InstructorActionType.CLEAN_CODE_REVIEW, studentId, conceptId, null, context, idempotencyKey);
    }

    @Override
    public Instructor generateReflectionAsync(UUID studentId, UUID conceptId, boolean passed, boolean isGraduation, String idempotencyKey) {
        // Guarded: check for existing successful generation
        List<Instructor> existing = instructorRepository.findByActionTypeAndStudentIdAndConceptIdAndStatus(
                InstructorActionType.REFLECT, studentId, conceptId, InstructorStatus.COMPLETED
        );
        if (!existing.isEmpty()) {
            log.info("REFLECT already completed for student: {} and concept: {}. Returning cached result.", studentId, conceptId);
            return existing.get(0);
        }

        Map<String, Object> context = new HashMap<>();
        context.put("passed", passed);
        context.put("isGraduation", isGraduation);

        return enqueueJob(InstructorActionType.REFLECT, studentId, conceptId, null, context, idempotencyKey);
    }

    // -------------------------------------------------------------------------
    // Job processing for background worker
    // -------------------------------------------------------------------------

    @Override
    public void processJob(UUID jobId) {
        log.info("Picking up job {} for processing", jobId);

        Instructor job = instructorRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Job {} not found in database", jobId);
            return;
        }

        if (job.getStatus() != InstructorStatus.QUEUED) {
            log.warn("Job {} is not in QUEUED state (current: {}). Skipping.", jobId, job.getStatus());
            return;
        }

        job.setStatus(InstructorStatus.RUNNING);
        job.setUpdatedAt(Instant.now());
        instructorRepository.save(job);

        try {
            String prompt = buildPrompt(job);
            String result = geminiClient.generate(prompt);

            job.setResult(result);
            job.setStatus(InstructorStatus.COMPLETED);
        } catch (Exception e) {
            log.error("Failed to execute AI generation for job " + jobId, e);
            job.setErrorMessage(e.getMessage());
            job.setStatus(InstructorStatus.FAILED);
        }

        job.setUpdatedAt(Instant.now());
        Instructor saved = instructorRepository.save(job);
        log.info("Completed job {} processing with status: {}", jobId, job.getStatus());
        eventPublisher.publishEvent(new InstructorJobCompletedEvent(this, saved));
    }

    @Override
    public Instructor getInstructorRecord(UUID id) {
        return instructorRepository.findById(id).orElse(null);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Instructor enqueueJob(
            InstructorActionType actionType,
            UUID studentId,
            UUID conceptId,
            UUID sessionId,
            Map<String, Object> context,
            String idempotencyKey
    ) {
        UUID jobId = UUID.randomUUID();
        Instructor job = Instructor.builder()
                .id(jobId)
                .actionType(actionType)
                .status(InstructorStatus.QUEUED)
                .studentId(studentId)
                .conceptId(conceptId)
                .sessionId(sessionId)
                .context(context)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Instructor saved = instructorRepository.save(job);
        redisTaskQueue.enqueue(QUEUE_NAME, jobId.toString());
        log.info("Enqueued job {} with actionType: {}", jobId, actionType);
        return saved;
    }

    private String buildPrompt(Instructor job) {
        return switch (job.getActionType()) {
            case BUILD_PRD_GENERATE ->
                    String.format("Generate a Product Requirement Document (PRD) for concept: %s. Target student: %s.",
                            job.getConceptId(), job.getStudentId());

            case AUDIO_REINFORCE ->
                    String.format("Generate audio reinforcement content for student: %s struggling with concept: %s (Session: %s). Focus on key conceptual blockers.",
                            job.getStudentId(), job.getConceptId(), job.getSessionId());

            case AUDIO_PRIME ->
                    String.format("Generate audio priming content for student: %s who passed concept: %s (Session: %s). Introduce next-step mental models.",
                            job.getStudentId(), job.getConceptId(), job.getSessionId());

            case MISSION_GENERATE -> {
                Map<String, Object> contextData = job.getContext();
                String flowType = contextData != null ? (String) contextData.get("flowType") : "FAILURE";
                if ("RESOLUTION".equals(flowType)) {
                    yield String.format(
                            "You are an expert AI tutor. A student has successfully passed an attempt on concept %s. " +
                            "Here is the context data: %s.\n" +
                            "Compare this passing attempt data with the existing open missions/pain points to determine which of them are resolved by this attempt.\n" +
                            "You must return the result as a raw JSON object in the following format (no other text, no markdown wrapper):\n" +
                            "{\n" +
                            "  \"resolvedMissionIds\": [\"uuid-string\"]\n" +
                            "}",
                            job.getConceptId(), contextData != null ? contextData.toString() : "None"
                    );
                } else {
                    yield String.format(
                            "You are an expert AI tutor. A student has failed an attempt on concept %s. " +
                            "Here is the context data: %s.\n" +
                            "Identify the specific conceptual pain points/understanding gaps, decide if they match any existing open missions from the context, " +
                            "and generate or update the advice for each pain point.\n" +
                            "You must return the result as a raw JSON array in the following format (no other text, no markdown wrapper):\n" +
                            "[\n" +
                            "  {\n" +
                            "    \"painPointDescription\": \"string\",\n" +
                            "    \"matchedMissionId\": \"string-or-null\",\n" +
                            "    \"conceptAndContext\": \"string\"\n" +
                            "  }\n" +
                            "]",
                            job.getConceptId(), contextData != null ? contextData.toString() : "None"
                    );
                }
            }

            case CLEAN_CODE_REVIEW -> {
                String code = job.getContext() != null ? (String) job.getContext().get("code") : "";
                yield String.format("Perform a clean code review for student: %s on concept: %s. Code submitted:\n%s",
                        job.getStudentId(), job.getConceptId(), code);
            }

            case REFLECT -> {
                Boolean passed = job.getContext() != null ? (Boolean) job.getContext().get("passed") : null;
                Boolean isGraduation = job.getContext() != null ? (Boolean) job.getContext().get("isGraduation") : null;
                yield String.format(
                        "Generate a personalized reflection prompt for student %s who has completed the concept build for concept %s. " +
                        "Completion status: %s. Is graduation: %s. Ask the student to reflect on their learning journey, " +
                        "what coding patterns they discovered, and what mental models they solidified during this concept build.",
                        job.getStudentId(), job.getConceptId(), Boolean.TRUE.equals(passed) ? "PASSED" : "FAILED", Boolean.TRUE.equals(isGraduation) ? "YES" : "NO"
                );
            }

            default -> throw new IllegalArgumentException("Unsupported async action type: " + job.getActionType());
        };
    }
}
