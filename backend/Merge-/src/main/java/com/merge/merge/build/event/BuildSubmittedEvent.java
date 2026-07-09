package com.merge.merge.build.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class BuildSubmittedEvent extends ApplicationEvent {

    private final UUID studentId;
    private final UUID conceptId;
    private final String code;
    private final String idempotencyKey;

    public BuildSubmittedEvent(Object source, UUID studentId, UUID conceptId, String code, String idempotencyKey) {
        super(source);
        this.studentId = studentId;
        this.conceptId = conceptId;
        this.code = code;
        this.idempotencyKey = idempotencyKey;
    }
}
