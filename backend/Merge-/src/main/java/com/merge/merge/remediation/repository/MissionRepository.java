package com.merge.merge.remediation.repository;

import com.merge.merge.remediation.models.Mission;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.UUID;

public interface MissionRepository extends MongoRepository<Mission, UUID> {
    List<Mission> findByStudentIdAndConceptIdAndPassed(UUID studentId, UUID conceptId, boolean passed);
}
