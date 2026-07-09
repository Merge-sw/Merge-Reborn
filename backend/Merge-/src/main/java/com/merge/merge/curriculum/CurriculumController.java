package com.merge.merge.curriculum;

import com.merge.merge.curriculum.dto.ConceptResponse;
import com.merge.merge.curriculum.dto.ResourceResponse;
import com.merge.merge.curriculum.dto.StageResponse;
import com.merge.merge.curriculum.service.ConceptService;
import com.merge.merge.curriculum.service.ResourceService;
import com.merge.merge.curriculum.service.StageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only curriculum endpoints. All require a valid JWT — enforced by
 * SecurityConfig's catch-all .anyRequest().authenticated() rule.
 *
 * <p>Controllers are thin: extract path/query params, delegate to the service,
 * map to DTO, return. No business logic here. ResourceNotFoundException
 * (unknown id) is handled by GlobalExceptionHandler → 404.</p>
 *
 * <p>No @ExceptionHandler in this class.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class CurriculumController {

    private final StageService stageService;
    private final ConceptService conceptService;
    private final ResourceService resourceService;

    // -------------------------------------------------------------------
    // Stages
    // -------------------------------------------------------------------

    @GetMapping("/stages")
    ResponseEntity<List<StageResponse>> listStages() {
        List<StageResponse> stages = stageService.listAll().stream()
                .map(StageResponse::from)
                .toList();
        return ResponseEntity.ok(stages);
    }

    @GetMapping("/stages/{id}")
    ResponseEntity<StageResponse> getStage(@PathVariable UUID id) {
        return ResponseEntity.ok(StageResponse.from(stageService.getById(id)));
    }

    // -------------------------------------------------------------------
    // Concepts
    // -------------------------------------------------------------------

    /**
     * Lists concepts for a given stage. stageId is required; no stage means
     * an empty list (stageId unknown) handled by a 404 from stageService if
     * callers want to validate the stage first, but listing concepts for a
     * non-existent stageId simply returns empty since the repository query
     * is by field value, not FK constraint.
     */
    @GetMapping("/concepts")
    ResponseEntity<List<ConceptResponse>> listConcepts(@RequestParam UUID stageId) {
        List<ConceptResponse> concepts = conceptService.listByStageId(stageId).stream()
                .map(ConceptResponse::from)
                .toList();
        return ResponseEntity.ok(concepts);
    }

    @GetMapping("/concepts/{id}")
    ResponseEntity<ConceptResponse> getConcept(@PathVariable UUID id) {
        return ResponseEntity.ok(ConceptResponse.from(conceptService.getById(id)));
    }

    @GetMapping("/concepts/{id}/resources")
    ResponseEntity<List<ResourceResponse>> listResourcesForConcept(@PathVariable UUID id) {
        // Validate the concept exists first so an unknown conceptId returns 404
        // rather than an empty list that looks like a valid-but-empty result.
        conceptService.getById(id);
        List<ResourceResponse> resources = resourceService.listByConceptId(id).stream()
                .map(ResourceResponse::from)
                .toList();
        return ResponseEntity.ok(resources);
    }
}
