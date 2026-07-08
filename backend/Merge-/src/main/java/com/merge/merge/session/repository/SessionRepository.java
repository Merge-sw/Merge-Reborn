package com.merge.merge.session.repository;

import com.merge.merge.session.model.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends MongoRepository<Session, UUID> {

    Optional<Session> findByStudentIdAndEndedAtIsNull(UUID studentId);

    /**
     * Used by the idle-timeout sweep: returns every open session whose last recorded
     * activity is older than the provided cutoff instant.
     */
    List<Session> findByEndedAtIsNullAndLastActivityAtBefore(Instant cutoff);
}
