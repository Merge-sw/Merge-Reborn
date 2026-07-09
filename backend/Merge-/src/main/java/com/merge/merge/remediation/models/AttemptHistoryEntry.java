package com.merge.merge.remediation.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptHistoryEntry {
    private Map<String, Object> attemptData;
    private Instant generatedAt;
}
