package com.merge.merge.curriculum.service.impl;

import com.merge.merge.curriculum.models.Stage;
import com.merge.merge.curriculum.repository.StageRepository;
import com.merge.merge.curriculum.service.ConceptService;
import com.merge.merge.curriculum.service.StageService;
import com.merge.merge.shared.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class StageServiceImpl implements StageService {

    private final StageRepository stageRepository;
    private final ConceptService conceptService;

    public StageServiceImpl(StageRepository stageRepository, ConceptService conceptService) {
        this.stageRepository = stageRepository;
        this.conceptService = conceptService;
    }

    @Override
    public Stage create(String name, int xpThreshold) {
        Stage stage = new Stage(name, xpThreshold);
        return stageRepository.save(stage);
    }

    @Override
    public Stage getById(UUID stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> ResourceNotFoundException.forId("Stage", stageId));
    }

    @Override
    public List<Stage> listAll() {
        return stageRepository.findAll();
    }

    @Override
    public void delete(UUID stageId) {
        long conceptCount = conceptService.countByStageId(stageId);
        if (conceptCount > 0) {
            throw new IllegalStateException("Cannot delete Stage with " + conceptCount + " dependent Concepts");
        }
        stageRepository.deleteById(stageId);
    }

    @Override
    public long getBuildPassRequired(UUID stageId) {
        return conceptService.countByStageId(stageId);
    }
}
