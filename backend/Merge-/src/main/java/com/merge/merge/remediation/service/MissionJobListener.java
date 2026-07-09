package com.merge.merge.remediation.service;

import com.merge.merge.ai.event.InstructorJobCompletedEvent;
import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.model.InstructorActionType;
import com.merge.merge.ai.model.InstructorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissionJobListener {

    private final RemediationService remediationService;

    @EventListener
    public void onInstructorJobCompleted(InstructorJobCompletedEvent event) {
        Instructor job = event.getJob();
        if (job.getActionType() == InstructorActionType.MISSION_GENERATE && job.getStatus() == InstructorStatus.COMPLETED) {
            log.info("Received completed MISSION_GENERATE job: {}", job.getId());
            remediationService.handleMissionGenerationResult(
                    job.getId(),
                    job.getStudentId(),
                    job.getConceptId(),
                    job.getResult(),
                    job.getContext()
            );
        }
    }
}
