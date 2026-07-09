package com.merge.merge.practice.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
@Setter
public class DrillPassedEvent extends ApplicationEvent {

    private final UUID studentId;
    private final UUID conceptId;
    private final UUID drillId;

    // Populated by the sync event listener
    private String result;
    private UUID instructorId;

    public DrillPassedEvent(Object source, UUID studentId, UUID conceptId, UUID drillId) {
        super(source);
        this.studentId = studentId;
        this.conceptId = conceptId;
        this.drillId = drillId;
    }
}
