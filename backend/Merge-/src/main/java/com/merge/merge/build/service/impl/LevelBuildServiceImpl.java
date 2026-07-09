package com.merge.merge.build.service.impl;

import com.merge.merge.build.models.BuildStatus;
import com.merge.merge.build.models.LevelBuild;
import com.merge.merge.build.repository.LevelBuildRepository;
import com.merge.merge.build.service.LevelBuildService;
import com.merge.merge.identity.service.StudentService;
import com.merge.merge.shared.queue.RedisTaskQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LevelBuildServiceImpl implements LevelBuildService {

    private final LevelBuildRepository levelBuildRepository;
    private final MongoTemplate mongoTemplate;
    private final StudentService studentService;
    private final RedisTaskQueue redisTaskQueue;

    @Override
    public boolean isLevelBuildPassed(UUID studentId, UUID stageId) {
        List<LevelBuild> builds = levelBuildRepository.findByStudentIdAndStageId(studentId, stageId);
        return builds.stream().anyMatch(LevelBuild::isPassed);
    }

    @Override
    public LevelBuild createLevelBuild(UUID studentId, UUID stageId, String githubLink, String sourceCode, String testSuite, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<LevelBuild> existing = levelBuildRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        LevelBuild lb = LevelBuild.builder()
                .id(UUID.randomUUID())
                .studentId(studentId)
                .stageId(stageId)
                .githubLink(githubLink)
                .sourceCode(sourceCode)
                .testSuite(testSuite)
                .idempotencyKey(idempotencyKey)
                .status(BuildStatus.QUEUED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        LevelBuild saved = levelBuildRepository.save(lb);
        redisTaskQueue.enqueue("build:job:queue", saved.getId().toString());
        return saved;
    }

    @Override
    public LevelBuild getLevelBuildRecord(UUID id) {
        return levelBuildRepository.findById(id).orElse(null);
    }

    @Override
    public boolean awardLevelXpOnce(UUID levelBuildId, int amount) {
        Query query = new Query(Criteria.where("id").is(levelBuildId).and("xpAwarded").is(0));
        Update update = new Update().set("xpAwarded", amount);
        LevelBuild updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                LevelBuild.class
        );
        if (updated != null) {
            studentService.awardXp(updated.getStudentId(), amount);
            return true;
        }
        return false;
    }
}
