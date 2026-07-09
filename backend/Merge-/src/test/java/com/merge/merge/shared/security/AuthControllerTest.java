package com.merge.merge.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.identity.repository.StudentRepository;
import com.merge.merge.identity.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real HTTP layer via MockMvc: DTO validation, status codes,
 * response shape, and cookie attributes as they actually appear on the wire.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private AuthService authService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @MockitoBean  private EmailService emailService;

    @AfterEach
    void cleanUp() {
        studentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // -------------------------------------------------------------------
    // POST /api/v1/auth/register
    // -------------------------------------------------------------------

    @Test
    void register_validRequest_returns200WithCorrectShapeAndRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correcthorse123","name":"Ada"}
                                """.strip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash");

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().contains("refresh_token=");
    }

    @Test
    void register_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"correcthorse123","name":"Ada"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"short1","name":"Ada"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_malformedJson_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"differentpass1","name":"Ada Two"}
                                """.strip()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    // -------------------------------------------------------------------
    // POST /api/v1/auth/login
    // -------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200AndRefreshCookieHasSecurityAttributes() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correcthorse123"}
                                """.strip()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash");

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void login_wrongPassword_returns401WithGenericMessage() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"wrongpassword1"}
                                """.strip()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Must not reveal whether the email exists in the system.
        assertThat(body).doesNotContain("no account", "does not exist", "UsernameNotFoundException");
        // Response must come through the shared handler, not a raw Spring error.
        assertThat(body).contains("Invalid credentials");
    }

    @Test
    void login_invalidRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // -------------------------------------------------------------------

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validCookie_returns200WithRotatedToken() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correcthorse123"}
                                """.strip()))
                .andReturn();
        var refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        var newCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertThat(newCookie).isNotNull();
        assertThat(newCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        // The old refresh token must now be dead.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------
    // POST /api/v1/auth/logout
    // -------------------------------------------------------------------

    @Test
    void logout_clearsRefreshCookie() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correcthorse123"}
                                """.strip()))
                .andReturn();
        var refreshCookie = loginResult.getResponse().getCookie("refresh_token");

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().containsIgnoringCase("Max-Age=0");

        // The revoked token must now be dead.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------
    // POST /api/v1/auth/password-reset
    // -------------------------------------------------------------------

    @Test
    void passwordResetRequest_validEmail_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com"}
                                """.strip()))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetRequest_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetConfirm_invalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"not-a-real-token","newPassword":"brandnewpass123"}
                                """.strip()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetConfirm_validToken_allowsLoginWithNewPassword() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com"}
                                """.strip()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("ada@example.com"), anyString(), bodyCaptor.capture());
        String rawToken = bodyCaptor.getValue().lines()
                .filter(line -> line.startsWith("Use this token"))
                .findFirst()
                .map(line -> line.substring(line.lastIndexOf(' ') + 1))
                .orElseThrow();

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"brandnewpass123"}
                                """.strip().formatted(rawToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"brandnewpass123"}
                                """.strip()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correcthorse123"}
                                """.strip()))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------
    // Protected endpoints
    // -------------------------------------------------------------------

    @Test
    void protectedEndpoint_noAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/students/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_validAccessToken_returns200WithStudentDto() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correcthorse123"}
                                """.strip()))
                .andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult meResult = mockMvc.perform(get("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada"))
                .andReturn();

        // Explicit no-leak check at the controller test layer.
        assertThat(meResult.getResponse().getContentAsString()).doesNotContain("passwordHash");
    }

    // -------------------------------------------------------------------
    // Rate limiting
    // -------------------------------------------------------------------

    @Test
    void sixthLoginAttemptWithinWindow_returns429() throws Exception {
        authService.register("ada@example.com", "correcthorse123", "Ada");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ada@example.com","password":"wrongpassword1"}
                                    """.strip()))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"wrongpassword1"}
                                """.strip()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too many requests"));
    }

    // -------------------------------------------------------------------
    // Error shape — responses must come through GlobalExceptionHandler
    // -------------------------------------------------------------------

    @Test
    void allErrorResponsesHaveConsistentProblemDetailShape() throws Exception {
        // 400 from validation
        MvcResult r400 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"correcthorse123","name":"Ada"}
                                """.strip()))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body400 = r400.getResponse().getContentAsString();
        assertThat(body400).contains("title");
        assertThat(body400).contains("detail");
        assertThat(body400).doesNotContain("stackTrace");
        assertThat(body400).doesNotContain("exception");
    }
}
