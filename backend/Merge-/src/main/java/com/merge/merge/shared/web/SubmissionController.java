package com.merge.merge.shared.web;

import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.service.InstructorService;
import com.merge.merge.build.models.ConceptBuild;
import com.merge.merge.build.models.LevelBuild;
import com.merge.merge.build.service.ConceptBuildService;
import com.merge.merge.build.service.LevelBuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Shared controller exposing a unified polling endpoint for all asynchronous
 * tasks (AI generations, concept builds, and level capstone builds).
 */
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
@Slf4j
public class SubmissionController {

    private final InstructorService instructorService;
    private final ConceptBuildService conceptBuildService;
    private final LevelBuildService levelBuildService;

    /**
     * Polls the status and results of any asynchronous task (AI job, ConceptBuild, or LevelBuild) by its ID.
     * Returns 200 with the matching record, or 404 if not found in any domain.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getSubmission(@PathVariable UUID id) {
        log.info("Polling submission status for ID: {}", id);

        // 1. Check AI / Instructor jobs
        Instructor instructorJob = instructorService.getInstructorRecord(id);
        if (instructorJob != null) {
            log.info("Found matching Instructor job for ID: {}", id);
            return ResponseEntity.ok(instructorJob);
        }

        // 2. Check ConceptBuild submissions
        ConceptBuild conceptBuild = conceptBuildService.getConceptBuildRecord(id);
        if (conceptBuild != null) {
            log.info("Found matching ConceptBuild for ID: {}", id);
            return ResponseEntity.ok(conceptBuild);
        }

        // 3. Check LevelBuild submissions
        LevelBuild levelBuild = levelBuildService.getLevelBuildRecord(id);
        if (levelBuild != null) {
            log.info("Found matching LevelBuild for ID: {}", id);
            return ResponseEntity.ok(levelBuild);
        }

        log.warn("Submission ID {} not found in any service repository.", id);
        return ResponseEntity.notFound().build();
    }
}
