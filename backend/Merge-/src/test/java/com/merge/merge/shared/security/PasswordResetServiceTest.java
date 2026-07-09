package com.merge.merge.shared.security;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class PasswordResetServiceTest {

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StringRedisTemplate redisTemplate;
    @MockitoBean private EmailService emailService;

    @AfterEach
    void cleanUp() {
        studentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private Student seedStudent(String email, String rawPassword) {
        Student s = new Student(UUID.randomUUID(), email,
                passwordEncoder.encode(rawPassword), "Test", null, null);
        return studentRepository.save(s);
    }

    @Test
    void requestResetSendsTokenToTheCorrectEmail() {
        seedStudent("ada@example.com", "original123");

        passwordResetService.requestReset("ada@example.com");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).send(eq("ada@example.com"), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("expires in 20 minutes").contains("once");
    }

    @Test
    void requestResetForUnknownEmailSendsNothing() {
        passwordResetService.requestReset("nobody@example.com");
        verify(emailService, times(0)).send(anyString(), anyString(), anyString());
    }

    @Test
    void tokenIsSingleUse() {
        Student student = seedStudent("ada@example.com", "original123");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        passwordResetService.requestReset("ada@example.com");
        verify(emailService).send(eq("ada@example.com"), anyString(), bodyCaptor.capture());
        String rawToken = extractToken(bodyCaptor.getValue());

        passwordResetService.confirmReset(rawToken, "brandnewpass123");

        Student updated = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("brandnewpass123", updated.getPasswordHash())).isTrue();
        // Old password must no longer match.
        assertThat(passwordEncoder.matches("original123", updated.getPasswordHash())).isFalse();

        // Second use of the same token must fail.
        assertThatThrownBy(() -> passwordResetService.confirmReset(rawToken, "anotherpass456"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> passwordResetService.confirmReset("not-a-real-token", "brandnewpass123"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    private static String extractToken(String body) {
        return body.lines()
                .filter(line -> line.startsWith("Use this token"))
                .findFirst()
                .map(line -> line.substring(line.lastIndexOf(' ') + 1))
                .orElseThrow();
    }
}
