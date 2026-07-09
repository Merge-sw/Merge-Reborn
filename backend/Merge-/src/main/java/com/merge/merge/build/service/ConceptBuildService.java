package com.merge.merge.build.service;

import com.merge.merge.build.models.ConceptBuild;
import java.util.UUID;

/**
 * Service interface for managing concept build progressions and retrieval.
 */
public interface ConceptBuildService {

    /**
     * Checks if the student has successfully passed the concept build for the given concept.
     */
    boolean isConceptBuildPassed(UUID studentId, UUID conceptId);

    /**
     * Creates and enqueues a ConceptBuild task.
     */
    ConceptBuild createConceptBuild(UUID studentId, UUID conceptId, String githubLink, String sourceCode, String testSuite, String idempotencyKey);

    /**
     * Retrieves the ConceptBuild record.
     */
    ConceptBuild getConceptBuildRecord(UUID id);

    /**
     * Awards XP to the student for passing the concept build.
     * Guarantees a single-payout by checking that XP has not already been awarded.
     *
     * @return true if XP was successfully awarded, false if it was already awarded.
     */
    boolean awardXpOnce(UUID conceptBuildId, int amount);
}
