package com.merge.merge.build.service;

import com.merge.merge.build.models.LevelBuild;

import java.util.UUID;

public interface LevelBuildService {
    boolean isLevelBuildPassed(UUID studentId, UUID stageId);
    LevelBuild createLevelBuild(UUID studentId, UUID stageId, String githubLink, String sourceCode, String testSuite, String idempotencyKey);
    LevelBuild getLevelBuildRecord(UUID id);
    boolean awardLevelXpOnce(UUID levelBuildId, int amount);
}
