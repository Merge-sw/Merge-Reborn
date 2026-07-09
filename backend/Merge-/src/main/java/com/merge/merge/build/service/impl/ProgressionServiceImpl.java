package com.merge.merge.build.service.impl;

import com.merge.merge.build.service.ConceptBuildService;
import com.merge.merge.build.service.LevelBuildService;
import com.merge.merge.build.service.ProgressionService;
import com.merge.merge.curriculum.models.Concept;
import com.merge.merge.curriculum.models.Stage;
import com.merge.merge.curriculum.service.ConceptService;
import com.merge.merge.curriculum.service.StageService;
import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressionServiceImpl implements ProgressionService {

    private final StudentService studentService;
    private final StageService stageService;
    private final ConceptService conceptService;
    private final ConceptBuildService conceptBuildService;
    private final LevelBuildService levelBuildService;

    @Override
    public boolean canPromote(UUID studentId, UUID stageId) {
        // Condition 1: Every ConceptBuild in the stage is passed
        List<Concept> stageConcepts = conceptService.listByStageId(stageId);
        boolean allConceptsPassed = stageConcepts.stream()
                .allMatch(c -> conceptBuildService.isConceptBuildPassed(studentId, c.getId()));

        // Condition 2: Total student XP >= stage.xpThreshold
        Student student = studentService.getById(studentId);
        Stage stage = stageService.getById(stageId);
        boolean xpMet = student.getXp() >= stage.getXpThreshold();

        // Condition 3: The LevelBuild for the stage is passed
        boolean levelBuildPassed = levelBuildService.isLevelBuildPassed(studentId, stageId);

        log.info("Promotion check for student={}, stage={}: allConceptsPassed={}, xpMet={}, levelBuildPassed={}",
                studentId, stageId, allConceptsPassed, xpMet, levelBuildPassed);

        return allConceptsPassed && xpMet && levelBuildPassed;
    }

    @Override
    public boolean promoteIfEligible(UUID studentId, UUID stageId) {
        if (!canPromote(studentId, stageId)) {
            log.info("Student {} is not eligible for promotion from stage {}", studentId, stageId);
            return false;
        }

        // Determine the next stage by sorting all stages by their xpThreshold ascending
        List<Stage> allStages = stageService.listAll().stream()
                .sorted(Comparator.comparingInt(Stage::getXpThreshold))
                .collect(Collectors.toList());

        UUID nextStageId = null;
        for (int i = 0; i < allStages.size(); i++) {
            if (allStages.get(i).getId().equals(stageId)) {
                if (i + 1 < allStages.size()) {
                    nextStageId = allStages.get(i + 1).getId();
                }
                break;
            }
        }

        if (nextStageId != null) {
            log.info("Promoting student {} from stage {} to next stage {}", studentId, stageId, nextStageId);
            studentService.advanceToStage(studentId, nextStageId);
            return true;
        } else {
            // No next stage found; student has graduated the curriculum (final stage capstone completed).
            // We grant internship eligibility as final project completion eligibility is gated.
            log.info("Student {} has graduated the final stage {}! Granting internship eligibility.", studentId, stageId);
            studentService.grantInternshipEligibility(studentId);
            return true;
        }
    }
}
