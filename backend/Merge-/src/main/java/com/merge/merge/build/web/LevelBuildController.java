package com.merge.merge.build.web;

import com.merge.merge.build.dto.CreateLevelBuildRequest;
import com.merge.merge.build.models.LevelBuild;
import com.merge.merge.build.service.LevelBuildService;
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
@RequestMapping("/api/v1/level-builds")
@RequiredArgsConstructor
public class LevelBuildController {

    private final LevelBuildService levelBuildService;

    @PostMapping
    public ResponseEntity<LevelBuild> createLevelBuild(
            @Valid @RequestBody CreateLevelBuildRequest request,
            Authentication authentication
    ) {
        UUID studentId = (UUID) authentication.getPrincipal();
        LevelBuild lb = levelBuildService.createLevelBuild(
                studentId, request.stageId(), request.githubLink(),
                request.sourceCode(), request.testSuite(), request.idempotencyKey()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(lb);
    }
}
