package com.merge.merge.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathEntry {
    private ActionType actionType;
    private UUID conceptId;
    private Instant timestamp;
    private Result result;
    private Mood moodAtAction;
    private Boolean wasRequired;
    private TopicRelevance topicRelevance;
    private InquiryDepth inquiryDepth;
}
