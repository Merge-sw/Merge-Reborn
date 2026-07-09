package com.merge.merge.shared.security;

import com.merge.merge.identity.models.Student;
import com.merge.merge.identity.service.AuthService;
import com.merge.merge.shared.security.dto.AuthResponse;
import com.merge.merge.shared.security.dto.LoginRequest;
import com.merge.merge.shared.security.dto.PasswordReset;
import com.merge.merge.shared.security.dto.PasswordResetRequest;
import com.merge.merge.shared.security.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authentication endpoints. All exception mapping is handled by
 * {@link com.merge.merge.shared.GlobalExceptionHandler}; this controller
 * contains no @ExceptionHandler methods of its own.
 *
 * <p>Validation failures from @Valid are caught by the global handler and
 * returned as 400 ProblemDetail. DuplicateEmailException → 409,
 * BadCredentialsException → 401, RateLimitExceededException → 429,
 * InvalidRefreshTokenException / TokenReuseDetectedException → 401,
 * all via the shared handler.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    /** True in all environments except local dev override (cookie.secure=false in dev profile). */
    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @PostMapping("/register")
    @RateLimited(key = "register", limit = 3, windowSeconds = 3600)
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        Student student = authService.register(request.email(), request.password(), request.name());
        return issueTokens(student.getId());
    }

    @PostMapping("/login")
    @RateLimited(key = "login", limit = 5, windowSeconds = 900, byEmail = true)
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return issueTokens(user.getStudentId());
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException("No refresh token cookie present");
        }
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
        return withRefreshCookie(jwtService.issueAccessToken(rotated.studentId()), rotated.newTokenId());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie("", 0).toString())
                .build();
    }

    @PostMapping("/password-reset/request")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/password-reset/confirm")
    ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordReset request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<AuthResponse> issueTokens(UUID studentId) {
        String refreshTokenId = refreshTokenService.issue(studentId);
        return withRefreshCookie(jwtService.issueAccessToken(studentId), refreshTokenId);
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(String accessToken, String refreshTokenId) {
        ResponseCookie cookie = buildRefreshCookie(refreshTokenId,
                (int) RefreshTokenService.REFRESH_TOKEN_TTL.toSeconds());
        AuthResponse body = new AuthResponse(accessToken, JwtService.ACCESS_TOKEN_TTL.toSeconds());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    private ResponseCookie buildRefreshCookie(String value, int maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
