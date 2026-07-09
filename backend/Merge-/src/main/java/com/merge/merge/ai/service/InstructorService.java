package com.merge.merge.ai.service;

import com.merge.merge.ai.model.Instructor;

import java.util.Map;
import java.util.UUID;

public interface InstructorService {

    // -------------------------------------------------------------------------
    // Direct-call APIs (Sync and Async)
    // -------------------------------------------------------------------------

    /**
     * Handles live chat message from a student.
     * Synchronous (calls Gemini in-thread).
     */
    Instructor chatInteraction(UUID studentId, UUID sessionId, String message);

    /**
     * Determines whether to trigger AUDIO_PRIME or AUDIO_REINFORCE when session is exhausted.
     * Evaluates student's concept build passed status and enqueues the appropriate action.
     * Asynchronous (returns QUEUED job).
     */
    Instructor handleSessionExhausted(UUID studentId, UUID sessionId, UUID conceptId);

    /**
     * Triggers audio reinforcement.
     * Asynchronous (returns QUEUED job).
     */
    Instructor audioReinforce(UUID studentId, UUID sessionId, UUID conceptId);

    /**
     * Triggers audio priming.
     * Asynchronous (returns QUEUED job).
     */
    Instructor audioPrime(UUID studentId, UUID sessionId, UUID conceptId);

    /**
     * Generates a mission after drill or build attempt fails.
     * Asynchronous (returns QUEUED job).
     */
    Instructor missionGenerate(UUID studentId, UUID conceptId, Map<String, Object> context);

    // -------------------------------------------------------------------------
    // Event-reaction APIs (Sync and Async)
    // -------------------------------------------------------------------------

    /**
     * Reacts to a student requesting a drill.
     * Synchronous (calls Gemini in-thread).
     */
    Instructor generateDrillSync(UUID studentId, UUID conceptId);

    /**
     * Reacts to a drill passing.
     * Synchronous (calls Gemini in-thread).
     */
    Instructor generateComprehensionSync(UUID studentId, UUID conceptId, UUID drillId);

    /**
     * Reacts to concept build unlocking.
     * Asynchronous (returns QUEUED job).
     */
    Instructor generateBuildPrdAsync(UUID studentId, UUID conceptId, String idempotencyKey);

    /**
     * Reacts to build submission.
     * Asynchronous (returns QUEUED job).
     */
    Instructor generateCleanCodeReviewAsync(UUID studentId, UUID conceptId, String code, String idempotencyKey);

    /**
     * Reacts to build completion or graduation.
     * Asynchronous (returns QUEUED job).
     */
    Instructor generateReflectionAsync(UUID studentId, UUID conceptId, boolean passed, boolean isGraduation, String idempotencyKey);

    // -------------------------------------------------------------------------
    // Job processing for background worker
    // -------------------------------------------------------------------------

    /**
     * Background worker pickup for asynchronous jobs.
     */
    void processJob(UUID jobId);

    /**
     * Retrieves an Instructor job/record.
     */
    Instructor getInstructorRecord(UUID id);
}
