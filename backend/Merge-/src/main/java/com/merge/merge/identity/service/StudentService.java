package com.merge.merge.identity.service;

import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student create(String name, String details, UUID stageId) {
        Student student = new Student(UUID.randomUUID(), name, details, stageId);
        return studentRepository.save(student);
    }

    public Student getById(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("no Student with id " + studentId));
    }

    public Student awardXp(UUID studentId, int amount) {
        Student student = getById(studentId);
        student.addXp(amount);
        return studentRepository.save(student);
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
