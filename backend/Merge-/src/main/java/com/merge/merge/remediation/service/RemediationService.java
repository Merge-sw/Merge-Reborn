package com.merge.merge.remediation.service;

import java.util.Map;
import java.util.UUID;

public interface RemediationService {
    /**
     * Entry point called directly when a student fails a drill or concept build.
     * Fetches open missions, structures the AI payload, and triggers asynchronous mission generation.
     */
    void handleFailure(UUID studentId, UUID conceptId, String failureSource, Map<String, Object> attemptData);


    /**
     * Entry point called directly when a student passes a drill or concept build.
     * Fetches open missions, structures the AI payload, and triggers asynchronous resolution checking.
     */
    void handlePass(UUID studentId, UUID conceptId, String source, Map<String, Object> attemptData);

    /**
     * Callback handler executed asynchronously when the AI Orchestration job completes.
     * Parses the LLM's response, matches or creates missions, and updates persistent records.
     */
    void handleMissionGenerationResult(UUID jobId, UUID studentId, UUID conceptId, String llmResult, Map<String, Object> originalContext);
}
