package com.merge.merge.identity.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SfiaScores {

    private Integer programming;
    private Integer systemDesign;
    private Integer testing;
    private Integer security;
    private Integer dataManagement;
    private Integer analytics;
    private Integer deployment;
    private Integer problemSolving;

    public SfiaScores(Integer programming, Integer systemDesign, Integer testing, Integer security,
                       Integer dataManagement, Integer analytics, Integer deployment, Integer problemSolving) {
        this.programming = requireInRange(programming, "programming");
        this.systemDesign = requireInRange(systemDesign, "systemDesign");
        this.testing = requireInRange(testing, "testing");
        this.security = requireInRange(security, "security");
        this.dataManagement = requireInRange(dataManagement, "dataManagement");
        this.analytics = requireInRange(analytics, "analytics");
        this.deployment = requireInRange(deployment, "deployment");
        this.problemSolving = requireInRange(problemSolving, "problemSolving");
    }

    private static Integer requireInRange(Integer value, String fieldName) {
        if (value != null && (value < 1 || value > 7)) {
            throw new IllegalArgumentException(fieldName + " must be between 1 and 7, got " + value);
        }
        return value;
    }
}
