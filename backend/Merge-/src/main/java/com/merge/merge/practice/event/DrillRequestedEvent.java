package com.merge.merge.practice.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
@Setter
public class DrillRequestedEvent extends ApplicationEvent {

    private final UUID studentId;
    private final UUID conceptId;

    // Populated by the sync event listener
    private String result;
    private UUID instructorId;

    public DrillRequestedEvent(Object source, UUID studentId, UUID conceptId) {
        super(source);
        this.studentId = studentId;
        this.conceptId = conceptId;
    }
}
