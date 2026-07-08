package com.merge.merge.identity.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedConcept {

    private UUID conceptId;
    private int failCount;
    private Instant lastFailedAt;
    private String knowledgeGap;

    public FailedConcept(UUID conceptId) {
        this.conceptId = conceptId;
    }

    public void recordFailure(String knowledgeGap, Instant failedAt) {
        this.failCount++;
        this.lastFailedAt = failedAt;
        this.knowledgeGap = knowledgeGap;
    }
}
