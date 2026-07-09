package com.merge.merge.build.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ConceptBuildUnlockedEvent extends ApplicationEvent {

    private final UUID studentId;
    private final UUID conceptId;
    private final String idempotencyKey;

    public ConceptBuildUnlockedEvent(Object source, UUID studentId, UUID conceptId, String idempotencyKey) {
        super(source);
        this.studentId = studentId;
        this.conceptId = conceptId;
        this.idempotencyKey = idempotencyKey;
    }
}
