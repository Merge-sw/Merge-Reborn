package com.merge.merge.curriculum.repository;

import com.merge.merge.curriculum.models.Resource;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceRepository extends MongoRepository<Resource, UUID> {

    long countByConceptId(UUID conceptId);

    /**
     * Used by CurriculumController to list all resources belonging to a concept.
     * The conceptId field on Resource carries a MongoDB index (see @Indexed on the model).
     */
    List<Resource> findByConceptId(UUID conceptId);
}
