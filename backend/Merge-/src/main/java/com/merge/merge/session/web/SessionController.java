package com.merge.merge.session.web;

import com.merge.merge.session.model.EndReason;
import com.merge.merge.session.model.Session;
import com.merge.merge.session.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
     * Opens (or returns the existing open) session for a student.
     *
     * <pre>POST /sessions</pre>
     *
     * <p>Request body: {@code { "studentId": "...", "mood": "FRESH" | "OKAY" | "EXHAUSTED" }}</p>
     * <p>Returns 201 with the session on creation, 200 if an open session already existed.</p>
     */
    @PostMapping
    ResponseEntity<Session> createOrGetSession(@Valid @RequestBody CreateSessionRequest request) {
        boolean existed = sessionService.hasOpenSession(request.studentId());
        Session session = sessionService.getOrCreateOpenSession(request.studentId(), request.mood());
        HttpStatus status = existed ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(session);
    }

    /**
     * Fetches a session by id.
     *
     * <pre>GET /sessions/{id}</pre>
     *
     * <p>Returns 200 with the session body.</p>
     * <p>Returns 404 if the session does not exist.</p>
     */
    @GetMapping("/{id}")
    ResponseEntity<Session> getSession(@PathVariable UUID id) {
        Session session = sessionService.getSession(id);
        return ResponseEntity.ok(session);
    }

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
    ResponseEntity<Session> appendAction(@PathVariable UUID id,
                                         @Valid @RequestBody AppendActionRequest request) {
        Session updated = sessionService.appendAction(id, request);
        return ResponseEntity.ok(updated);
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
    ResponseEntity<Session> endSession(@PathVariable UUID id,
                                       @Valid @RequestBody EndSessionRequest request) {
        if (!CLIENT_SETTABLE_REASONS.contains(request.reason())) {
            throw new InvalidEndReasonException(request.reason());
        }
        Session ended = sessionService.endSession(id, request.reason());
        return ResponseEntity.ok(ended);
    }
}
