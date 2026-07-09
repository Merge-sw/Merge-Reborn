package com.merge.merge.curriculum.service.impl;

import com.merge.merge.curriculum.models.Concept;
import com.merge.merge.curriculum.models.PredefinedContentRef;
import com.merge.merge.curriculum.repository.ConceptRepository;
import com.merge.merge.curriculum.service.ConceptService;
import com.merge.merge.curriculum.service.ResourceService;
import com.merge.merge.shared.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConceptServiceImpl implements ConceptService {

    private final ConceptRepository conceptRepository;
    private final ResourceService resourceService;

    public ConceptServiceImpl(ConceptRepository conceptRepository, ResourceService resourceService) {
        this.conceptRepository = conceptRepository;
        this.resourceService = resourceService;
    }

    @Override
    public Concept create(UUID stageId, PredefinedContentRef content) {
        Concept concept = new Concept(stageId, content);
        return conceptRepository.save(concept);
    }

    @Override
    public Concept getById(UUID conceptId) {
        return conceptRepository.findById(conceptId)
                .orElseThrow(() -> ResourceNotFoundException.forId("Concept", conceptId));
    }

    @Override
    public List<Concept> listByStageId(UUID stageId) {
        return conceptRepository.findByStageId(stageId);
    }

    @Override
    public void delete(UUID conceptId) {
        long resourceCount = resourceService.countByConceptId(conceptId);
        if (resourceCount > 0) {
            throw new IllegalStateException("Cannot delete Concept with " + resourceCount + " dependent Resources");
        }
        conceptRepository.deleteById(conceptId);
    }

    @Override
    public long countByStageId(UUID stageId) {
        return conceptRepository.countByStageId(stageId);
    }
}
