package com.merge.merge.build.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class BuildCompletedEvent extends ApplicationEvent {

    private final UUID studentId;
    private final UUID conceptId;
    private final boolean passed;
    private final boolean isGraduation;
    private final String idempotencyKey;

    public BuildCompletedEvent(Object source, UUID studentId, UUID conceptId, boolean passed, boolean isGraduation, String idempotencyKey) {
        super(source);
        this.studentId = studentId;
        this.conceptId = conceptId;
        this.passed = passed;
        this.isGraduation = isGraduation;
        this.idempotencyKey = idempotencyKey;
    }
}
