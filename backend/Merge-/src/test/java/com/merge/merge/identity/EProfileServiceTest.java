package com.merge.merge.identity;

import com.merge.merge.TestcontainersConfiguration;
import com.merge.merge.identity.models.EProfile;
import com.merge.merge.identity.models.LevelOfThinking;
import com.merge.merge.identity.models.NoveltyOfThinking;
import com.merge.merge.identity.models.SfiaScores;
import com.merge.merge.identity.repository.EProfileRepository;
import com.merge.merge.identity.service.EProfileService;
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
class EProfileServiceTest {

    @Autowired
    private EProfileService eProfileService;

    @Autowired
    private EProfileRepository eProfileRepository;

    @AfterEach
    void cleanUp() {
        eProfileRepository.deleteAll();
    }

    @Test
    void createForStudentStartsAllNull() {
        UUID studentId = UUID.randomUUID();

        EProfile eProfile = eProfileService.createForStudent(studentId);

        assertThat(eProfile.getStudentId()).isEqualTo(studentId);
        assertThat(eProfile.getCompetencyData().getSfiaScores()).isNull();
        assertThat(eProfile.getCompetencyData().getProjectCompletionRate()).isNull();
        assertThat(eProfile.getCompetencyData().getConsistencyScore()).isNull();
        assertThat(eProfile.getCompetencyData().getLevelOfThinking()).isNull();
    }

    @Test
    void updateSfiaScoresPersists() {
        UUID studentId = UUID.randomUUID();
        eProfileService.createForStudent(studentId);
        SfiaScores scores = new SfiaScores(3, 2, 4, 1, 3, 2, 1, 4);

        eProfileService.updateSfiaScores(studentId, scores);

        EProfile found = eProfileService.getByStudentId(studentId);
        assertThat(found.getCompetencyData().getSfiaScores().getProgramming()).isEqualTo(3);
        assertThat(found.getCompetencyData().getSfiaScores().getSecurity()).isEqualTo(1);
    }

    @Test
    void sfiaScoreOutOfRangeRejected() {
        assertThatThrownBy(() -> new SfiaScores(8, 2, 4, 1, 3, 2, 1, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SfiaScores(0, 2, 4, 1, 3, 2, 1, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateConsistencyScoreOutOfRangeRejected() {
        UUID studentId = UUID.randomUUID();
        eProfileService.createForStudent(studentId);

        assertThatThrownBy(() -> eProfileService.updateConsistencyScore(studentId, 1.5f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateThinkingAssessmentPersistsBothFields() {
        UUID studentId = UUID.randomUUID();
        eProfileService.createForStudent(studentId);

        eProfileService.updateThinkingAssessment(studentId, LevelOfThinking.ADVANCED, NoveltyOfThinking.HIGH);

        EProfile found = eProfileService.getByStudentId(studentId);
        assertThat(found.getCompetencyData().getLevelOfThinking()).isEqualTo(LevelOfThinking.ADVANCED);
        assertThat(found.getCompetencyData().getNoveltyOfThinking()).isEqualTo(NoveltyOfThinking.HIGH);
    }
}
