package com.merge.merge.curriculum.repository;

import com.merge.merge.curriculum.models.Concept;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ConceptRepository extends MongoRepository<Concept, UUID> {

    long countByStageId(UUID stageId);

    /**
     * Used by CurriculumController to list all concepts belonging to a stage.
     * The stageId field on Concept carries a MongoDB index (see @Indexed on the model).
     */
    List<Concept> findByStageId(UUID stageId);
}
