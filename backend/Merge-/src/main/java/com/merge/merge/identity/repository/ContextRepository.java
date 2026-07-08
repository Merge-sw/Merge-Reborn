package com.merge.merge.identity.repository;

import com.merge.merge.identity.models.Context;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContextRepository extends MongoRepository<Context, UUID> {

    Optional<Context> findByStudentId(UUID studentId);
}
