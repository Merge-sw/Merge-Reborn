package com.merge.merge.session.web;

import com.merge.merge.session.service.SessionAlreadyEndedException;
import com.merge.merge.session.service.SessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler scoped to the session module's controllers.
 *
 * <p>Translates domain exceptions into RFC 9457 ProblemDetail responses so that
 * controllers stay free of try/catch blocks and all error shapes are consistent.</p>
 */
@RestControllerAdvice(basePackages = "com.merge.merge.session.web")
class SessionExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail handleNotFound(SessionNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Session not found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(SessionAlreadyEndedException.class)
    ProblemDetail handleAlreadyEnded(SessionAlreadyEndedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Session already ended");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(InvalidEndReasonException.class)
    ProblemDetail handleInvalidReason(InvalidEndReasonException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid end reason");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request body");
        problem.setDetail(detail);
        return problem;
    }
}
