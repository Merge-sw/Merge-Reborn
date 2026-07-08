package com.merge.merge.identity.repository;

import com.merge.merge.identity.models.EProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface EProfileRepository extends MongoRepository<EProfile, UUID> {

    Optional<EProfile> findByStudentId(UUID studentId);
}
