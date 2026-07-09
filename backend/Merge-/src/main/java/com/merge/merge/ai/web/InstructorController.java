package com.merge.merge.ai.web;

import com.merge.merge.ai.model.Instructor;
import com.merge.merge.ai.service.InstructorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing endpoints for checking the status and result of
 * Instructor async tasks (submissions).
 */
@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class InstructorController {

    private final InstructorService instructorService;

    /**
     * Polls the status of an asynchronous Instructor task.
     * Returns 200 with the full job record, or 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Instructor> getSubmission(@PathVariable UUID id) {
        Instructor record = instructorService.getInstructorRecord(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }
}
