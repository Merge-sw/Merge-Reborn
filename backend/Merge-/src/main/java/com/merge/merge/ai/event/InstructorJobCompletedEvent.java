package com.merge.merge.ai.event;

import com.merge.merge.ai.model.Instructor;
import org.springframework.context.ApplicationEvent;

public class InstructorJobCompletedEvent extends ApplicationEvent {
    private final Instructor job;

    public InstructorJobCompletedEvent(Object source, Instructor job) {
        super(source);
        this.job = job;
    }

    public Instructor getJob() {
        return job;
    }
}
