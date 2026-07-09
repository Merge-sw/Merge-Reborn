package com.merge.merge.build.web;

import com.merge.merge.build.dto.CreateConceptBuildRequest;
import com.merge.merge.build.models.ConceptBuild;
import com.merge.merge.build.service.ConceptBuildService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/concept-builds")
@RequiredArgsConstructor
public class ConceptBuildController {

    private final ConceptBuildService conceptBuildService;

    @PostMapping
    public ResponseEntity<ConceptBuild> createConceptBuild(
            @Valid @RequestBody CreateConceptBuildRequest request,
            Authentication authentication
    ) {
        UUID studentId = (UUID) authentication.getPrincipal();
        ConceptBuild cb = conceptBuildService.createConceptBuild(
                studentId, request.conceptId(), request.githubLink(),
                request.sourceCode(), request.testSuite(), request.idempotencyKey()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(cb);
    }
}
