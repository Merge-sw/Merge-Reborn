package com.merge.merge.curriculum.dto;

import com.merge.merge.curriculum.models.Stage;

import java.util.UUID;

/**
 * Response DTO for Stage. Explicit field mapping — callers cannot return the
 * raw entity and accidentally expose fields added later.
 */
public record StageResponse(
        UUID id,
        String name,
        int xpThreshold
) {
    public static StageResponse from(Stage stage) {
        return new StageResponse(
                stage.getId(),
                stage.getName(),
                stage.getXpThreshold()
        );
    }
}
