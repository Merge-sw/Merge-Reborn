package com.merge.merge.build.service.impl;

import com.merge.merge.build.models.BuildStatus;
import com.merge.merge.build.models.ConceptBuild;
import com.merge.merge.build.repository.ConceptBuildRepository;
import com.merge.merge.build.service.ConceptBuildService;
import com.merge.merge.identity.service.StudentService;
import com.merge.merge.shared.queue.RedisTaskQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service implementation for ConceptBuildService.
 */
@Service
@RequiredArgsConstructor
public class ConceptBuildServiceImpl implements ConceptBuildService {

    private final ConceptBuildRepository conceptBuildRepository;
    private final MongoTemplate mongoTemplate;
    private final StudentService studentService;
    private final RedisTaskQueue redisTaskQueue;

    @Override
    public boolean isConceptBuildPassed(UUID studentId, UUID conceptId) {
        return conceptBuildRepository.findByStudentIdAndConceptId(studentId, conceptId)
                .map(ConceptBuild::isPassed)
                .orElse(false);
    }

    @Override
    public ConceptBuild createConceptBuild(UUID studentId, UUID conceptId, String githubLink, String sourceCode, String testSuite, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<ConceptBuild> existing = conceptBuildRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ConceptBuild cb = ConceptBuild.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .conceptId(conceptId)
                .githubLink(githubLink)
                .sourceCode(sourceCode)
                .testSuite(testSuite)
                .idempotencyKey(idempotencyKey)
                .status(BuildStatus.QUEUED)
                .build();

        ConceptBuild saved = conceptBuildRepository.save(cb);
        redisTaskQueue.enqueue("build:job:queue", saved.getId().toString());
        return saved;
    }

    @Override
    public ConceptBuild getConceptBuildRecord(UUID id) {
        return conceptBuildRepository.findById(id).orElse(null);
    }

    @Override
    public boolean awardXpOnce(UUID conceptBuildId, int amount) {
        Query query = new Query(Criteria.where("id").is(conceptBuildId).and("xpAwarded").is(0));
        Update update = new Update().set("xpAwarded", amount);
        ConceptBuild updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ConceptBuild.class
        );
        if (updated != null) {
            studentService.awardXp(updated.getStudentId(), amount);
            return true;
        }
        return false;
    }
}
