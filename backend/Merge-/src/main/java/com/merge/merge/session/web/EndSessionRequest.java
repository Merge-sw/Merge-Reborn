package com.merge.merge.session.web;

import com.merge.merge.session.model.EndReason;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /sessions/{id}/end}.
 *
 * <p>Only {@link EndReason#NAVIGATED_AWAY} and {@link EndReason#EXHAUSTED} are
 * accepted here.  {@link EndReason#COMPLETED} is set by the Build-passed event
 * (Milestone 9) and {@link EndReason#IDLE_TIMEOUT} is set by the idle sweep
 * (Milestone 2).  Neither may be sent by the client.</p>
 */
public record EndSessionRequest(@NotNull EndReason reason) {}
