package com.merge.merge.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "instructors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instructor {

    @Id
    private UUID id;

    private InstructorActionType actionType;

    private InstructorStatus status;

    private String result;

    private String errorMessage;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    private UUID studentId;

    private UUID conceptId;

    private UUID sessionId;

    // Treated as opaque JSON blob/personalized data (e.g. for MISSION_GENERATE)
    private Map<String, Object> context;

    private Instant createdAt;

    private Instant updatedAt;
}
