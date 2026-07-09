package com.merge.merge.shared.security;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.identity.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boundary-condition tests for the password length constraint on RegisterRequest
 * and PasswordReset. The regex is: ^(?=.*[A-Za-z])(?=.*\d).{12,72}$
 *
 * Tests verify exact edges: 11 chars (invalid), 12 chars (valid), 72 chars (valid),
 * 73 chars (invalid). Each must contain at least one letter and one digit to isolate
 * the length boundary from the lookahead constraint.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private EmailService emailService;

    @AfterEach
    void cleanUp() {
        studentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // 11 chars = 10 'a' + '1' → fails .{12,72}
    private static final String PASSWORD_11 = "aaaaaaaaaa1";
    // 12 chars = 11 'a' + '1' → passes .{12,72}
    private static final String PASSWORD_12 = "aaaaaaaaaaa1";
    // 72 chars = 71 'a' + '1' → passes .{12,72}
    private static final String PASSWORD_72 = "a".repeat(71) + "1";
    // 73 chars = 72 'a' + '1' → fails .{12,72}
    private static final String PASSWORD_73 = "a".repeat(72) + "1";

    @Test
    void register_password11chars_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"%s","name":"Test"}
                                """.strip().formatted(PASSWORD_11)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_password12chars_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"%s","name":"Test"}
                                """.strip().formatted(PASSWORD_12)))
                .andExpect(status().isOk());
    }

    @Test
    void register_password72chars_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"%s","name":"Test"}
                                """.strip().formatted(PASSWORD_72)))
                .andExpect(status().isOk());
    }

    @Test
    void register_password73chars_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"%s","name":"Test"}
                                """.strip().formatted(PASSWORD_73)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordAllDigits_returns400() throws Exception {
        // Passes length but fails letter lookahead
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"123456789012","name":"Test"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordAllLetters_returns400() throws Exception {
        // Passes length but fails digit lookahead
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"abcdefghijkl","name":"Test"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emptyPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"","name":"Test"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emptyEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"correcthorse123","name":"Test"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emptyName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@example.com","password":"correcthorse123","name":""}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }
}
