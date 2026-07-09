package com.merge.merge.build.repository;

import com.merge.merge.build.models.ConceptBuild;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConceptBuildRepository extends MongoRepository<ConceptBuild, UUID> {
    Optional<ConceptBuild> findByStudentIdAndConceptId(UUID studentId, UUID conceptId);
}
