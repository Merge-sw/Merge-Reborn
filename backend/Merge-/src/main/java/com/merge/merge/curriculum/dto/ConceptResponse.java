package com.merge.merge.curriculum.dto;

import com.merge.merge.curriculum.models.Concept;
import com.merge.merge.curriculum.models.PredefinedContentRef;

import java.util.UUID;

/**
 * Response DTO for Concept. Explicit field mapping.
 */
public record ConceptResponse(
        UUID id,
        UUID stageId,
        PredefinedContentRefResponse predefinedContentRef
) {
    public static ConceptResponse from(Concept concept) {
        return new ConceptResponse(
                concept.getId(),
                concept.getStageId(),
                PredefinedContentRefResponse.from(concept.getPredefinedContentRef())
        );
    }

    /**
     * Nested DTO for PredefinedContentRef. The entity carries @NotBlank
     * validation annotations that belong on the write side, not the read side;
     * this DTO exposes the data cleanly without those constraints.
     */
    public record PredefinedContentRefResponse(
            String failureScenario,
            String teachingObjective,
            String coreContent
    ) {
        public static PredefinedContentRefResponse from(PredefinedContentRef ref) {
            if (ref == null) {
                return null;
            }
            return new PredefinedContentRefResponse(
                    ref.getFailureScenario(),
                    ref.getTeachingObjective(),
                    ref.getCoreContent()
            );
        }
    }
}
