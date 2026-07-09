package com.merge.merge.shared;

import com.merge.merge.identity.DuplicateEmailException;
import com.merge.merge.shared.security.InvalidRefreshTokenException;
import com.merge.merge.shared.security.InvalidResetTokenException;
import com.merge.merge.shared.security.RateLimitExceededException;
import com.merge.merge.shared.security.TokenReuseDetectedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single, authoritative exception-to-HTTP mapping for the entire application.
 * No controller or module builds its own @ExceptionHandler; everything flows
 * through here. This guarantees one consistent error response shape and that
 * no stack traces ever reach a client.
 *
 * <p>Response shape: RFC 7807 {@link ProblemDetail}, with {@code type},
 * {@code title}, {@code status}, and {@code detail}. No {@code stackTrace},
 * no internal message that leaks system internals.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -----------------------------------------------------------------------
    // Authentication and token errors
    // -----------------------------------------------------------------------

    /**
     * Spring Security throws BadCredentialsException from DaoAuthenticationProvider
     * when the password does not match. Mapped to 401 with a deliberately generic
     * message that does not confirm whether the email exists or not.
     */
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> handleBadCredentials() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid credentials");
        problem.setDetail("Email or password is incorrect");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ProblemDetail> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid refresh token");
        problem.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    /**
     * Token reuse is a security event, not a routine expiry. Logged at WARN
     * so it is searchable in production logs even though the HTTP response
     * is the same shape as a normal 401 to avoid leaking detection internals
     * to a potential attacker.
     */
    @ExceptionHandler(TokenReuseDetectedException.class)
    ResponseEntity<ProblemDetail> handleTokenReuse(TokenReuseDetectedException e) {
        log.warn("Refresh token reuse detected — all sessions revoked: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Session invalidated");
        problem.setDetail("Your session is no longer valid. Please log in again.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    ResponseEntity<ProblemDetail> handleInvalidResetToken(InvalidResetTokenException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid reset token");
        problem.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // -----------------------------------------------------------------------
    // Resource and domain errors
    // -----------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ProblemDetail> handleDuplicateEmail(DuplicateEmailException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Email already registered");
        problem.setDetail(e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // -----------------------------------------------------------------------
    // Rate limiting
    // -----------------------------------------------------------------------

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimitExceeded() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Too many requests");
        problem.setDetail("Rate limit exceeded, try again later");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    // -----------------------------------------------------------------------
    // Validation errors
    // -----------------------------------------------------------------------

    /**
     * Bean Validation failures from @Valid on controller parameters. Returns
     * 400 with a human-readable detail listing every field that failed.
     * No internal message or class name ever included.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationFailure(MethodArgumentNotValidException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request is invalid");
        problem.setDetail(detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // -----------------------------------------------------------------------
    // Catch-all: must never expose stack traces or internal messages
    // -----------------------------------------------------------------------

    /**
     * Malformed JSON or unreadable request body (e.g. syntax errors). Returns
     * 400, not 500 — the client sent bad input, not the server faulting.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request body");
        problem.setDetail("Request body could not be read. Check JSON syntax.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        // Log at ERROR for alerting, but never send the detail to the client.
        log.error("Unhandled exception", e);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
