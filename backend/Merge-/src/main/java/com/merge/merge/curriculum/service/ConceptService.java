package com.merge.merge.curriculum.service;

import com.merge.merge.curriculum.models.Concept;
import com.merge.merge.curriculum.models.PredefinedContentRef;

import java.util.List;
import java.util.UUID;

public interface ConceptService {
    Concept create(UUID stageId, PredefinedContentRef content);
    Concept getById(UUID conceptId);
    List<Concept> listByStageId(UUID stageId);
    void delete(UUID conceptId);
    long countByStageId(UUID stageId);
}
