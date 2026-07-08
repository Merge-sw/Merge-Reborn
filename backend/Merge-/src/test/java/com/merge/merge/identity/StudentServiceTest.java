package com.merge.merge.identity;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.repository.StudentRepository;
import com.merge.merge.identity.service.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StudentServiceTest {

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @AfterEach
    void cleanUp() {
        studentRepository.deleteAll();
    }

    @Test
    void createsStudentWithZeroXpAndNotInternshipEligible() {
        UUID stageId = UUID.randomUUID();

        Student student = studentService.create("Ada", "some free text", stageId);

        assertThat(student.getId()).isNotNull();
        assertThat(student.getName()).isEqualTo("Ada");
        assertThat(student.getDetails()).isEqualTo("some free text");
        assertThat(student.getStageId()).isEqualTo(stageId);
        assertThat(student.getXp()).isZero();
        assertThat(student.isInternshipEligible()).isFalse();
    }

    @Test
    void getByIdReturnsPersistedStudent() {
        Student created = studentService.create("Ada", "details", UUID.randomUUID());

        Student found = studentService.getById(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    void getByIdThrowsWhenStudentDoesNotExist() {
        assertThatThrownBy(() -> studentService.getById(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void awardXpAccumulates() {
        Student student = studentService.create("Ada", "details", UUID.randomUUID());

        studentService.awardXp(student.getId(), 50);
        Student afterFirstAward = studentService.getById(student.getId());
        assertThat(afterFirstAward.getXp()).isEqualTo(50);

        studentService.awardXp(student.getId(), 30);
        Student afterSecondAward = studentService.getById(student.getId());
        assertThat(afterSecondAward.getXp()).isEqualTo(80);
    }

    @Test
    void awardXpRejectsNegativeAmount() {
        Student student = studentService.create("Ada", "details", UUID.randomUUID());

        assertThatThrownBy(() -> studentService.awardXp(student.getId(), -10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advanceToStageUpdatesStageId() {
        Student student = studentService.create("Ada", "details", UUID.randomUUID());
        UUID nextStageId = UUID.randomUUID();

        studentService.advanceToStage(student.getId(), nextStageId);

        assertThat(studentService.getById(student.getId()).getStageId()).isEqualTo(nextStageId);
    }

    @Test
    void grantInternshipEligibilitySetsFlag() {
        Student student = studentService.create("Ada", "details", UUID.randomUUID());
        assertThat(student.isInternshipEligible()).isFalse();

        studentService.grantInternshipEligibility(student.getId());

        assertThat(studentService.getById(student.getId()).isInternshipEligible()).isTrue();
    }
}
