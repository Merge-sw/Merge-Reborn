package com.merge.merge.session.web;

import com.merge.merge.session.model.Mood;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /sessions}.
 *
 * <p>The client supplies the student's current mood; the server derives
 * {@link com.merge.merge.session.model.SessionType} from it and returns the
 * open session (creating it if one does not already exist).</p>
 */
public record CreateSessionRequest(
        @NotNull UUID studentId,
        @NotNull Mood mood
) {}
