package com.merge.merge.curriculum.service;

import com.merge.merge.curriculum.models.Stage;

import java.util.List;
import java.util.UUID;

public interface StageService {
    Stage create(String name, int xpThreshold);
    Stage getById(UUID stageId);
    List<Stage> listAll();
    void delete(UUID stageId);
    long getBuildPassRequired(UUID stageId);
}
