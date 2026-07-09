package com.merge.merge.remediation.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "missions")
public class Mission {
    @Id
    private UUID id;
    private UUID conceptId;
    private UUID studentId;
    private String painPointDescription;
    private String conceptAndContext;
    private List<AttemptHistoryEntry> attemptHistory;
    private boolean passed;
    private Instant createdAt;
    private Instant updatedAt;
}
