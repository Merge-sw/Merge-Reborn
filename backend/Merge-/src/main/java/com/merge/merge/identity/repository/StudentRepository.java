package com.merge.merge.identity.repository;

import com.merge.merge.identity.models.Student;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface StudentRepository extends MongoRepository<Student, UUID> {
}
