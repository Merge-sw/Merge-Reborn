package com.merge.merge.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sessions")
@CompoundIndex(name = "unique_open_session_per_student", def = "{'studentId': 1}", unique = true, partialFilter = "{'endedAt': {'$eq': null}}")
public class Session {
    @Id
    private UUID id;
    private UUID studentId;
    private Instant startedAt;
    private Instant lastActivityAt;
    private Instant endedAt;
    private EndReason endReason;
    private Mood mood;
    private SessionType type;
    private List<PathEntry> path;
}
