package com.merge.merge.identity.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "e_profiles")
public class EProfile {

    @Id
    private UUID id;

    @Indexed(unique = true)
    private UUID studentId;

    private CompetencyData competencyData = new CompetencyData();

    public EProfile(UUID id, UUID studentId) {
        this.id = id;
        this.studentId = studentId;
    }

    public void updateSfiaScores(SfiaScores sfiaScores) {
        competencyData.updateSfiaScores(sfiaScores);
    }

    public void updateProjectCompletionRate(float rate) {
        competencyData.updateProjectCompletionRate(rate);
    }

    public void updateConsistencyScore(float score) {
        competencyData.updateConsistencyScore(score);
    }

    public void updateThinkingAssessment(LevelOfThinking levelOfThinking, NoveltyOfThinking noveltyOfThinking) {
        competencyData.updateThinkingAssessment(levelOfThinking, noveltyOfThinking);
    }
}
