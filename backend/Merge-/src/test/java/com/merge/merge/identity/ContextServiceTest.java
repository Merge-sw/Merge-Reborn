package com.merge.merge.identity;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.identity.models.Context;
import com.merge.merge.identity.models.LearningPreference;
import com.merge.merge.identity.models.Motivation;
import com.merge.merge.identity.models.PreferredLanguage;
import com.merge.merge.identity.models.StaticData;
import com.merge.merge.identity.repository.ContextRepository;
import com.merge.merge.identity.service.ContextService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ContextServiceTest {

    @Autowired
    private ContextService contextService;

    @Autowired
    private ContextRepository contextRepository;

    @AfterEach
    void cleanUp() {
        contextRepository.deleteAll();
    }

    @Test
    void createForStudentStartsWithNoStaticDataAndEmptyDynamicData() {
        UUID studentId = UUID.randomUUID();

        Context context = contextService.createForStudent(studentId);

        assertThat(context.getStudentId()).isEqualTo(studentId);
        assertThat(context.getPersonalisedData().getStaticData()).isNull();
        assertThat(context.getPersonalisedData().getDynamicData().getFailedConcepts()).isEmpty();
    }

    @Test
    void recordScoutIngestionSetsStaticDataOnce() {
        UUID studentId = UUID.randomUUID();
        contextService.createForStudent(studentId);
        StaticData staticData = new StaticData(2, PreferredLanguage.JAVA, Motivation.JOB);

        contextService.recordScoutIngestion(studentId, staticData);

        Context found = contextService.getByStudentId(studentId);
        assertThat(found.getPersonalisedData().getStaticData().getYearsOfExperience()).isEqualTo(2);
        assertThat(found.getPersonalisedData().getStaticData().getPreferredLanguage()).isEqualTo(PreferredLanguage.JAVA);
    }

    @Test
    void recordScoutIngestionTwiceThrows() {
        UUID studentId = UUID.randomUUID();
        contextService.createForStudent(studentId);
        StaticData staticData = new StaticData(2, PreferredLanguage.JAVA, Motivation.JOB);
        contextService.recordScoutIngestion(studentId, staticData);

        assertThatThrownBy(() -> contextService.recordScoutIngestion(studentId, staticData))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordFailedConceptAccumulatesFailCountForSameConcept() {
        UUID studentId = UUID.randomUUID();
        contextService.createForStudent(studentId);
        UUID conceptId = UUID.randomUUID();

        contextService.recordFailedConcept(studentId, conceptId, "off by one in loop bound");
        contextService.recordFailedConcept(studentId, conceptId, "still off by one");

        Context found = contextService.getByStudentId(studentId);
        assertThat(found.getPersonalisedData().getDynamicData().getFailedConcepts()).hasSize(1);
        assertThat(found.getPersonalisedData().getDynamicData().getFailedConcept(conceptId).orElseThrow().getFailCount())
                .isEqualTo(2);
        assertThat(found.getPersonalisedData().getDynamicData().getFailedConcept(conceptId).orElseThrow().getKnowledgeGap())
                .isEqualTo("still off by one");
    }

    @Test
    void recordSuccessfulMissionApproachAppendsEntry() {
        UUID studentId = UUID.randomUUID();
        contextService.createForStudent(studentId);
        UUID conceptId = UUID.randomUUID();

        contextService.recordSuccessfulMissionApproach(studentId, conceptId, "walked through with a diagram first");

        Context found = contextService.getByStudentId(studentId);
        assertThat(found.getPersonalisedData().getDynamicData().getSuccessfulMissionApproaches()).hasSize(1);
    }

    @Test
    void updateLearningPreferenceIsNullableAndOverwritable() {
        UUID studentId = UUID.randomUUID();
        contextService.createForStudent(studentId);
        assertThat(contextService.getByStudentId(studentId).getPersonalisedData().getDynamicData().getLearningPreference())
                .isNull();

        contextService.updateLearningPreference(studentId, LearningPreference.STEP_BY_STEP);

        assertThat(contextService.getByStudentId(studentId).getPersonalisedData().getDynamicData().getLearningPreference())
                .isEqualTo(LearningPreference.STEP_BY_STEP);
    }
}
