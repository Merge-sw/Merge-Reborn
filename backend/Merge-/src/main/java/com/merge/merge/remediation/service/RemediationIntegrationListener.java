package com.merge.merge.remediation.service;

import com.merge.merge.build.event.BuildCompletedEvent;
import com.merge.merge.practice.event.DrillPassedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemediationIntegrationListener {

    private final RemediationService remediationService;

    @EventListener
    public void onDrillPassed(DrillPassedEvent event) {
        log.info("Received DrillPassedEvent for student: {}, concept: {}, drill: {}",
                event.getStudentId(), event.getConceptId(), event.getDrillId());

        Map<String, Object> attemptData = new HashMap<>();
        attemptData.put("drillId", event.getDrillId().toString());
        attemptData.put("result", event.getResult());

        remediationService.handlePass(event.getStudentId(), event.getConceptId(), "DRILL", attemptData);
    }

    @EventListener
    public void onBuildCompleted(BuildCompletedEvent event) {
        log.info("Received BuildCompletedEvent for student: {}, concept: {}, passed: {}",
                event.getStudentId(), event.getConceptId(), event.isPassed());

        Map<String, Object> attemptData = new HashMap<>();
        attemptData.put("isGraduation", event.isGraduation());
        attemptData.put("idempotencyKey", event.getIdempotencyKey());

        if (event.isPassed()) {
            remediationService.handlePass(event.getStudentId(), event.getConceptId(), "CONCEPT_BUILD", attemptData);
        } else {
            remediationService.handleFailure(event.getStudentId(), event.getConceptId(), "CONCEPT_BUILD", attemptData);
        }
    }
}
