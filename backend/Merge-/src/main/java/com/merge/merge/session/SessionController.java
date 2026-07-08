package com.merge.merge.session;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * REST interface for the Session resource.
 */
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
class SessionController {

    /**
     * The only reasons a client may supply when ending a session.
     *
     * <p>COMPLETED is set by the Build-passed event listener (Milestone 9).
     * IDLE_TIMEOUT is set by the scheduled sweep (Milestone 2).
     * Neither may come from the client.</p>
     */
    private static final Set<EndReason> CLIENT_SETTABLE_REASONS = Set.of(
            EndReason.NAVIGATED_AWAY,
            EndReason.EXHAUSTED
    );

    private final SessionService sessionService;

    /**
     * Appends one action to the session's path.
     *
     * <pre>POST /sessions/{id}/actions</pre>
     *
     * <p>Request body fields: {@code actionType} (required), {@code conceptId} (required),
     * {@code moodAtAction} (required), {@code result} (nullable, only DRILL_ATTEMPT /
     * CONCEPT_BUILD_ATTEMPT), {@code topicRelevance} and {@code inquiryDepth} (nullable,
     * only CHAT_INTERACTION).</p>
     *
     * <p>Returns 200 with the updated session on success.</p>
     * <p>Returns 404 if the session does not exist.</p>
     * <p>Returns 409 if the session is already ended.</p>
     */
    @PostMapping("/{id}/actions")
    ResponseEntity<?> appendAction(@PathVariable UUID id,
                                   @RequestBody AppendActionRequest request) {
        try {
            Session updated = sessionService.appendAction(id, request);
            return ResponseEntity.ok(updated);
        } catch (SessionNotFoundException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            problem.setTitle("Session not found");
            problem.setDetail(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        } catch (SessionAlreadyEndedException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
            problem.setTitle("Session already ended");
            problem.setDetail(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }
    }

    /**
     * Ends an open session.
     *
     * <pre>POST /sessions/{id}/end</pre>
     *
     * <p>Request body: {@code { "reason": "NAVIGATED_AWAY" | "EXHAUSTED" }}</p>
     * <p>Returns 200 with the updated session on success.</p>
     * <p>Returns 400 if the reason is not client-settable.</p>
     * <p>Returns 404 if the session does not exist.</p>
     * <p>Returns 409 if the session is already ended.</p>
     */
    @PostMapping("/{id}/end")
    ResponseEntity<?> endSession(@PathVariable UUID id,
                                 @RequestBody EndSessionRequest request) {
        if (request.reason() == null || !CLIENT_SETTABLE_REASONS.contains(request.reason())) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problem.setTitle("Invalid end reason");
            problem.setDetail("Reason must be one of: NAVIGATED_AWAY, EXHAUSTED. " +
                    "COMPLETED and IDLE_TIMEOUT are not client-settable.");
            return ResponseEntity.badRequest().body(problem);
        }

        try {
            Session ended = sessionService.endSession(id, request.reason());
            return ResponseEntity.ok(ended);
        } catch (SessionNotFoundException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            problem.setTitle("Session not found");
            problem.setDetail(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        } catch (SessionAlreadyEndedException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
            problem.setTitle("Session already ended");
            problem.setDetail(e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }
    }
}
