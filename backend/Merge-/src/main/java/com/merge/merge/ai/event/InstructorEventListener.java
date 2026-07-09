package com.merge.merge.ai.event;

import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.service.InstructorService;
import com.merge.merge.build.event.BuildCompletedEvent;
import com.merge.merge.build.event.BuildSubmittedEvent;
import com.merge.merge.build.event.ConceptBuildUnlockedEvent;
import com.merge.merge.practice.event.DrillPassedEvent;
import com.merge.merge.practice.event.DrillRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class InstructorEventListener {

    private final InstructorService instructorService;

    /**
     * Reacts synchronously to a student requesting a drill.
     * Calls Gemini in-thread and populates the event result fields.
     */
    @EventListener
    public void onDrillRequested(DrillRequestedEvent event) {
        log.info("Received DrillRequestedEvent for student: {}, concept: {}", event.getStudentId(), event.getConceptId());
        Instructor instructor = instructorService.generateDrillSync(event.getStudentId(), event.getConceptId());
        event.setResult(instructor.getResult());
        event.setInstructorId(instructor.getId());
    }

    /**
     * Reacts synchronously to a drill submission passing.
     * Calls Gemini in-thread and populates the event result fields.
     */
    @EventListener
    public void onDrillPassed(DrillPassedEvent event) {
        log.info("Received DrillPassedEvent for student: {}, concept: {}, drill: {}", event.getStudentId(), event.getConceptId(), event.getDrillId());
        Instructor instructor = instructorService.generateComprehensionSync(event.getStudentId(), event.getConceptId(), event.getDrillId());
        event.setResult(instructor.getResult());
        event.setInstructorId(instructor.getId());
    }

    /**
     * Reacts asynchronously to a concept build unlocking.
     * Creates and enqueues a background job.
     */
    @EventListener
    public void onConceptBuildUnlocked(ConceptBuildUnlockedEvent event) {
        log.info("Received ConceptBuildUnlockedEvent for student: {}, concept: {}", event.getStudentId(), event.getConceptId());
        instructorService.generateBuildPrdAsync(event.getStudentId(), event.getConceptId(), event.getIdempotencyKey());
    }

    /**
     * Reacts asynchronously to a build submission.
     * Creates and enqueues a background job.
     */
    @EventListener
    public void onBuildSubmitted(BuildSubmittedEvent event) {
        log.info("Received BuildSubmittedEvent for student: {}, concept: {}", event.getStudentId(), event.getConceptId());
        instructorService.generateCleanCodeReviewAsync(event.getStudentId(), event.getConceptId(), event.getCode(), event.getIdempotencyKey());
    }

    /**
     * Reacts asynchronously to a build completion.
     * Creates and enqueues a background job only if the build passed.
     */
    @EventListener
    public void onBuildCompleted(BuildCompletedEvent event) {
        log.info("Received BuildCompletedEvent for student: {}, concept: {}, passed: {}", event.getStudentId(), event.getConceptId(), event.isPassed());
        if (event.isPassed()) {
            instructorService.generateReflectionAsync(event.getStudentId(), event.getConceptId(), event.isPassed(), event.isGraduation(), event.getIdempotencyKey());
        } else {
            log.info("Build did not pass. Skipping reflection generation.");
        }
    }
}
