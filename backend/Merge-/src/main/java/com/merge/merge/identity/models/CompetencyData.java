package com.merge.merge.identity.models;

import lombok.Getter;

@Getter
public class CompetencyData {

    private SfiaScores sfiaScores;
    private Float projectCompletionRate;
    private Float consistencyScore;
    private LevelOfThinking levelOfThinking;
    private NoveltyOfThinking noveltyOfThinking;

    public void updateSfiaScores(SfiaScores sfiaScores) {
        this.sfiaScores = sfiaScores;
    }

    public void updateProjectCompletionRate(float projectCompletionRate) {
        this.projectCompletionRate = requireUnitInterval(projectCompletionRate, "projectCompletionRate");
    }

    public void updateConsistencyScore(float consistencyScore) {
        this.consistencyScore = requireUnitInterval(consistencyScore, "consistencyScore");
    }

    public void updateThinkingAssessment(LevelOfThinking levelOfThinking, NoveltyOfThinking noveltyOfThinking) {
        this.levelOfThinking = levelOfThinking;
        this.noveltyOfThinking = noveltyOfThinking;
    }

    private static float requireUnitInterval(float value, String fieldName) {
        if (value < 0f || value > 1f) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1, got " + value);
        }
        return value;
    }
}
