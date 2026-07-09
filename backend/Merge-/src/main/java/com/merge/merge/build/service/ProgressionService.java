package com.merge.merge.build.service;

import java.util.UUID;

public interface ProgressionService {
    /**
     * Checks whether the student satisfies all conditions to promote past the given stage.
     */
    boolean canPromote(UUID studentId, UUID stageId);

    /**
     * Promotes the student to the next stage if they are eligible.
     * Updates Student.stageId and grants internship eligibility if they graduate the final stage.
     *
     * @return true if successfully promoted, false otherwise.
     */
    boolean promoteIfEligible(UUID studentId, UUID stageId);
}
