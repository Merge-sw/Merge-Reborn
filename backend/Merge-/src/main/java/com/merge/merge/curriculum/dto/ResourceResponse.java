package com.merge.merge.curriculum.dto;

import com.merge.merge.curriculum.models.Resource;

import java.util.UUID;

/**
 * Response DTO for Resource. Explicit field mapping.
 */
public record ResourceResponse(
        UUID id,
        UUID conceptId,
        String type,
        String title,
        String url
) {
    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getConceptId(),
                resource.getType(),
                resource.getTitle(),
                resource.getUrl()
        );
    }
}
