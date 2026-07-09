package com.merge.merge.ai.repository;

import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.model.InstructorActionType;
import com.merge.merge.ai.model.InstructorStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstructorRepository extends MongoRepository<Instructor, UUID> {

    Optional<Instructor> findByIdempotencyKey(String idempotencyKey);

    List<Instructor> findByActionTypeAndStudentIdAndConceptIdAndStatus(
            InstructorActionType actionType,
            UUID studentId,
            UUID conceptId,
            InstructorStatus status
    );
}
