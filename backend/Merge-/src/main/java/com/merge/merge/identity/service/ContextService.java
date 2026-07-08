package com.merge.merge.identity.service;

import com.merge.merge.identity.models.Context;
import com.merge.merge.identity.models.LearningPreference;
import com.merge.merge.identity.models.StaticData;
import com.merge.merge.identity.repository.ContextRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ContextService {

    private final ContextRepository contextRepository;

    public ContextService(ContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    public Context createForStudent(UUID studentId) {
        Context context = new Context(UUID.randomUUID(), studentId);
        return contextRepository.save(context);
    }

    public Context getByStudentId(UUID studentId) {
        return contextRepository.findByStudentId(studentId)
                .orElseThrow(() -> new NoSuchElementException("no Context for studentId " + studentId));
    }

    public Context recordScoutIngestion(UUID studentId, StaticData staticData) {
        Context context = getByStudentId(studentId);
        context.recordScoutIngestion(staticData);
        return contextRepository.save(context);
    }

    /**
     * Identity's own half of a failed attempt: records the knowledge gap against Context's
     * dynamic data. This does NOT touch Drill. The PRD requires the knowledgeGap write and the
     * Drill status write to commit as one multi-document transaction, and requires the engineer
     * to explicitly decide and confirm which service owns that transaction boundary before it's
     * built, rather than defaulting silently. That decision has not been made yet, and Practice
     * (Ticket 3, owns Drill) does not exist yet either, so this method is deliberately scoped to
     * Identity's own write only. Do not call this as a substitute for the real cross-module
     * transaction once Practice exists.
     */
    public Context recordFailedConcept(UUID studentId, UUID conceptId, String knowledgeGap) {
        Context context = getByStudentId(studentId);
        context.recordFailedConcept(conceptId, knowledgeGap, Instant.now());
        return contextRepository.save(context);
    }

    public Context recordSuccessfulMissionApproach(UUID studentId, UUID conceptId, String approach) {
        Context context = getByStudentId(studentId);
        context.recordSuccessfulMissionApproach(conceptId, approach);
        return contextRepository.save(context);
    }

    public Context updateAvgSubmissionDurationSeconds(UUID studentId, int avgSubmissionDurationSeconds) {
        Context context = getByStudentId(studentId);
        context.updateAvgSubmissionDurationSeconds(avgSubmissionDurationSeconds);
        return contextRepository.save(context);
    }

    public Context updateLearningPreference(UUID studentId, LearningPreference learningPreference) {
        Context context = getByStudentId(studentId);
        context.updateLearningPreference(learningPreference);
        return contextRepository.save(context);
    }
}
