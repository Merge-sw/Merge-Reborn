package com.merge.merge.identity.models;

import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
public class DynamicData {

    private List<FailedConcept> failedConcepts = new ArrayList<>();
    private Integer avgSubmissionDurationSeconds;
    private LearningPreference learningPreference;
    private List<SuccessfulMissionApproach> successfulMissionApproaches = new ArrayList<>();

    public void recordFailedConcept(UUID conceptId, String knowledgeGap, Instant failedAt) {
        FailedConcept failedConcept = getFailedConcept(conceptId)
                .orElseGet(() -> {
                    FailedConcept created = new FailedConcept(conceptId);
                    failedConcepts.add(created);
                    return created;
                });
        failedConcept.recordFailure(knowledgeGap, failedAt);
    }

    public void recordSuccessfulMissionApproach(UUID conceptId, String approach) {
        successfulMissionApproaches.add(new SuccessfulMissionApproach(conceptId, approach));
    }

    public void updateAvgSubmissionDurationSeconds(int avgSubmissionDurationSeconds) {
        this.avgSubmissionDurationSeconds = avgSubmissionDurationSeconds;
    }

    public void updateLearningPreference(LearningPreference learningPreference) {
        this.learningPreference = learningPreference;
    }

    public Optional<FailedConcept> getFailedConcept(UUID conceptId) {
        return failedConcepts.stream()
                .filter(entry -> entry.getConceptId().equals(conceptId))
                .findFirst();
    }
}
