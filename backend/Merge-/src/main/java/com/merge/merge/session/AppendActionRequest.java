package com.merge.merge.session;

import java.util.UUID;

/**
 * Request body for {@code POST /sessions/{id}/actions}.
 *
 * <p>{@code wasRequired} and {@code timestamp} are never supplied by the caller —
 * the server derives and sets them.</p>
 *
 * <p>For {@link ActionType#CHAT_INTERACTION} entries, {@code topicRelevance} and
 * {@code inquiryDepth} carry the structured-output classification returned by the
 * AI Orchestration service.  For all other action types these fields are null.</p>
 *
 * <p>{@code result} is populated for {@link ActionType#DRILL_ATTEMPT} and
 * {@link ActionType#CONCEPT_BUILD_ATTEMPT} only; it is null for
 * {@link ActionType#RESOURCE_VIEW} and {@link ActionType#CHAT_INTERACTION}.</p>
 */
record AppendActionRequest(
        ActionType actionType,
        UUID conceptId,
        Mood moodAtAction,
        Result result,
        TopicRelevance topicRelevance,
        InquiryDepth inquiryDepth
) {}
