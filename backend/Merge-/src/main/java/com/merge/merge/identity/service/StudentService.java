package com.merge.merge.identity.service;

import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.repository.StudentRepository;
import com.merge.merge.shared.ResourceNotFoundException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final MongoTemplate mongoTemplate;

    public StudentService(StudentRepository studentRepository, MongoTemplate mongoTemplate) {
        this.studentRepository = studentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Creates a Student without auth credentials. Used in tests and any
     * non-auth flow that needs to seed a Student in the database. Students
     * created via registration go through AuthService instead, which sets
     * email and passwordHash in a single @Transactional write.
     */
    public Student create(String name, String details, UUID stageId) {
        Student student = new Student(UUID.randomUUID(), null, null, name, details, stageId);
        return studentRepository.save(student);
    }

    public Student getById(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> ResourceNotFoundException.forId("Student", studentId));
    }

    /**
     * Awards XP atomically using MongoDB $inc via findAndModify. A single
     * database command finds the document, increments xp, and returns the
     * updated document — no read-modify-write, no lost-update race under
     * concurrent Drill or Build completions.
     */
    public Student awardXp(UUID studentId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("xp amount must not be negative");
        }
        Query query = Query.query(Criteria.where("_id").is(studentId));
        Update update = new Update().inc("xp", amount);
        Student updated = mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().returnNew(true),
                Student.class);
        if (updated == null) {
            throw ResourceNotFoundException.forId("Student", studentId);
        }
        return updated;
    }

    public Student advanceToStage(UUID studentId, UUID stageId) {
        Student student = getById(studentId);
        student.advanceToStage(stageId);
        return studentRepository.save(student);
    }

    public Student grantInternshipEligibility(UUID studentId) {
        Student student = getById(studentId);
        student.grantInternshipEligibility();
        return studentRepository.save(student);
    }
}
