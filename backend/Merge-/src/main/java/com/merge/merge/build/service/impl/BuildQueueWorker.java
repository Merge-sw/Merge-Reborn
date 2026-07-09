package com.merge.merge.build.service.impl;

import com.merge.merge.ai.service.InstructorService;
import com.merge.merge.build.event.BuildCompletedEvent;
import com.merge.merge.build.models.BuildStatus;
import com.merge.merge.build.models.ConceptBuild;
import com.merge.merge.build.models.LevelBuild;
import com.merge.merge.build.repository.ConceptBuildRepository;
import com.merge.merge.build.repository.LevelBuildRepository;
import com.merge.merge.build.service.ConceptBuildService;
import com.merge.merge.build.service.LevelBuildService;
import com.merge.merge.build.service.ProgressionService;
import com.merge.merge.curriculum.models.Stage;
import com.merge.merge.curriculum.service.StageService;
import com.merge.merge.integration.judge0.Judge0Client;
import com.merge.merge.integration.judge0.Judge0Result;
import com.merge.merge.shared.queue.RedisTaskQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class BuildQueueWorker {

    private final RedisTaskQueue redisTaskQueue;
    private final ConceptBuildRepository conceptBuildRepository;
    private final LevelBuildRepository levelBuildRepository;
    private final ConceptBuildService conceptBuildService;
    private final LevelBuildService levelBuildService;
    private final ProgressionService progressionService;
    private final StageService stageService;
    private final InstructorService instructorService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskExecutor taskExecutor;
    private final Judge0Client judge0Client;

    private static final String QUEUE_NAME = "build:job:queue";
    private static final int CONCEPT_BUILD_XP = 20;
    private static final int LEVEL_BUILD_XP = 100;

    public BuildQueueWorker(
            RedisTaskQueue redisTaskQueue,
            ConceptBuildRepository conceptBuildRepository,
            LevelBuildRepository levelBuildRepository,
            ConceptBuildService conceptBuildService,
            LevelBuildService levelBuildService,
            ProgressionService progressionService,
            StageService stageService,
            InstructorService instructorService,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
            Judge0Client judge0Client
    ) {
        this.redisTaskQueue = redisTaskQueue;
        this.conceptBuildRepository = conceptBuildRepository;
        this.levelBuildRepository = levelBuildRepository;
        this.conceptBuildService = conceptBuildService;
        this.levelBuildService = levelBuildService;
        this.progressionService = progressionService;
        this.stageService = stageService;
        this.instructorService = instructorService;
        this.eventPublisher = eventPublisher;
        this.taskExecutor = taskExecutor;
        this.judge0Client = judge0Client;
    }

    @Scheduled(fixedDelay = 1000)
    public void pollQueue() {
        String taskId;
        while ((taskId = redisTaskQueue.dequeue(QUEUE_NAME)) != null) {
            final UUID id = UUID.fromString(taskId);
            log.info("BuildQueueWorker picked up build task ID {}. Dispatching...", id);

            taskExecutor.execute(() -> {
                try {
                    processBuild(id);
                } catch (Exception e) {
                    log.error("Unhandled exception in background processing of build task ID " + id, e);
                }
            });
        }
    }

    public void processBuild(UUID taskId) {
        // 1. Check if ConceptBuild
        ConceptBuild cb = conceptBuildRepository.findById(taskId).orElse(null);
        if (cb != null) {
            processConceptBuild(cb);
            return;
        }

        // 2. Check if LevelBuild
        LevelBuild lb = levelBuildRepository.findById(taskId).orElse(null);
        if (lb != null) {
            processLevelBuild(lb);
            return;
        }

        log.warn("Task ID {} not found in either ConceptBuild or LevelBuild collection.", taskId);
    }

    private void processConceptBuild(ConceptBuild cb) {
        if (cb.getStatus() != BuildStatus.QUEUED) {
            log.info("ConceptBuild {} is not in QUEUED state (current: {}). Skipping.", cb.getId(), cb.getStatus());
            return;
        }

        cb.setStatus(BuildStatus.RUNNING);
        conceptBuildRepository.save(cb);

        log.info("Running Judge0 execution for ConceptBuild {}", cb.getId());
        Judge0Result result = judge0Client.evaluate(cb.getSourceCode(), cb.getTestSuite());

        cb.setHiddenTestsPassed(result.passed());
        cb.setTddSuitePassed(result.passed());
        cb.setComprehensionCheckPassed(true);
        cb.setPassed(result.passed());
        cb.setStatus(result.passed() ? BuildStatus.PASSED : BuildStatus.FAILED);
        cb.setFeedback(result.passed() ? "Tests executed successfully. " + result.stdout()
                : "Execution failed. Stdout: " + result.stdout() + ", Stderr: " + result.stderr() + ", Compile: " + result.compileOutput());

        conceptBuildRepository.save(cb);

        // Award XP atomically (single-payout guard)
        conceptBuildService.awardXpOnce(cb.getId(), CONCEPT_BUILD_XP);

        // Publish event to trigger downstream Instructor reflection asynchronously
        eventPublisher.publishEvent(new BuildCompletedEvent(
                this, cb.getStudentId(), cb.getConceptId(), true, false, cb.getIdempotencyKey()
        ));

        log.info("Completed processing for ConceptBuild {}", cb.getId());
    }

    private void processLevelBuild(LevelBuild lb) {
        if (lb.getStatus() != BuildStatus.QUEUED) {
            log.info("LevelBuild {} is not in QUEUED state (current: {}). Skipping.", lb.getId(), lb.getStatus());
            return;
        }

        lb.setStatus(BuildStatus.RUNNING);
        levelBuildRepository.save(lb);

        log.info("Running Judge0 execution for LevelBuild {}", lb.getId());
        Judge0Result result = judge0Client.evaluate(lb.getSourceCode(), lb.getTestSuite());

        lb.setHiddenTestsPassed(result.passed());
        lb.setTddSuitePassed(result.passed());
        lb.setComprehensionCheckPassed(true);

        // 2. Call async reviews for pedagogical / dynamic EProfile competency evidence
        instructorService.generateCleanCodeReviewAsync(
                lb.getStudentId(), lb.getStageId(), lb.getGithubLink(), lb.getIdempotencyKey() + "-cc"
        );
        instructorService.evaluateSfiaAlignmentAsync(
                lb.getStudentId(), lb.getStageId(), lb.getIdempotencyKey() + "-sfia"
        );

        // Mock clean code review rubric score and SFIA alignment
        lb.setCleanCodeScore(85);
        lb.setSfiaAligned(true);

        // 3. Evaluate pass/fail based on Stage category (Cadet vs. Engineer & Above)
        Stage stage = stageService.getById(lb.getStageId());
        boolean isCadet = stage.getName().toLowerCase().contains("cadet");

        boolean passed;
        if (isCadet) {
            passed = lb.isHiddenTestsPassed() && lb.isTddSuitePassed() && lb.isComprehensionCheckPassed();
            log.info("Stage is Cadet. Skipping clean code and SFIA gating check for LevelBuild {}.", lb.getId());
        } else {
            passed = lb.isHiddenTestsPassed() && lb.isTddSuitePassed() && lb.isComprehensionCheckPassed()
                    && (lb.getCleanCodeScore() >= 70) && lb.isSfiaAligned();
            log.info("Stage is Engineer or above. Applying full 5-gate conjunction for LevelBuild {}.", lb.getId());
        }

        lb.setPassed(passed);
        lb.setStatus(passed ? BuildStatus.PASSED : BuildStatus.FAILED);
        lb.setFeedback(passed ? "Capstone build completed and passed all gate checks."
                : "Capstone build failed one or more gating criteria.");

        levelBuildRepository.save(lb);

        if (passed) {
            // Award Level XP atomically
            levelBuildService.awardLevelXpOnce(lb.getId(), LEVEL_BUILD_XP);

            // Attempt promotion
            boolean promoted = progressionService.promoteIfEligible(lb.getStudentId(), lb.getStageId());

            // Check if this was graduation (final stage completed)
            List<Stage> allStages = stageService.listAll().stream()
                    .sorted(Comparator.comparingInt(Stage::getXpThreshold))
                    .collect(Collectors.toList());
            boolean isGraduation = allStages.isEmpty() || allStages.get(allStages.size() - 1).getId().equals(lb.getStageId());

            // Publish completed event to trigger AI reflection
            eventPublisher.publishEvent(new BuildCompletedEvent(
                    this, lb.getStudentId(), lb.getStageId(), true, isGraduation, lb.getIdempotencyKey()
            ));
        }

        log.info("Completed processing for LevelBuild {} with status {}", lb.getId(), lb.getStatus());
    }
}
