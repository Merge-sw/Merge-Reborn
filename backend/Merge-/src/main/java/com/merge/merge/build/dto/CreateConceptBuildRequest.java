package com.merge.merge.build.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateConceptBuildRequest(
        @NotNull
        UUID conceptId,

        @NotBlank
        String githubLink,

        @NotBlank
        String sourceCode,

        @NotBlank
        String testSuite,

        String idempotencyKey
) {}
