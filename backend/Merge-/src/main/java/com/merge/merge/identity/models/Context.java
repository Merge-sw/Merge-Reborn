package com.merge.merge.identity.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "contexts")
public class Context {

    @Id
    private UUID id;

    @Indexed(unique = true)
    private UUID studentId;

    private PersonalisedData personalisedData = new PersonalisedData();

    public Context(UUID id, UUID studentId) {
        this.id = id;
        this.studentId = studentId;
    }

    public void recordScoutIngestion(StaticData staticData) {
        personalisedData.recordScoutIngestion(staticData);
    }

    public void recordFailedConcept(UUID conceptId, String knowledgeGap, Instant failedAt) {
        personalisedData.getDynamicData().recordFailedConcept(conceptId, knowledgeGap, failedAt);
    }

    public void recordSuccessfulMissionApproach(UUID conceptId, String approach) {
        personalisedData.getDynamicData().recordSuccessfulMissionApproach(conceptId, approach);
    }

    public void updateAvgSubmissionDurationSeconds(int avgSubmissionDurationSeconds) {
        personalisedData.getDynamicData().updateAvgSubmissionDurationSeconds(avgSubmissionDurationSeconds);
    }

    public void updateLearningPreference(LearningPreference learningPreference) {
        personalisedData.getDynamicData().updateLearningPreference(learningPreference);
    }
}
