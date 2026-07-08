package com.merge.merge.identity.service;

import com.merge.merge.identity.models.EProfile;
import com.merge.merge.identity.models.LevelOfThinking;
import com.merge.merge.identity.models.NoveltyOfThinking;
import com.merge.merge.identity.models.SfiaScores;
import com.merge.merge.identity.repository.EProfileRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EProfileService {

    private final EProfileRepository eProfileRepository;

    public EProfileService(EProfileRepository eProfileRepository) {
        this.eProfileRepository = eProfileRepository;
    }

    public EProfile createForStudent(UUID studentId) {
        EProfile eProfile = new EProfile(UUID.randomUUID(), studentId);
        return eProfileRepository.save(eProfile);
    }

    public EProfile getByStudentId(UUID studentId) {
        return eProfileRepository.findByStudentId(studentId)
                .orElseThrow(() -> new NoSuchElementException("no EProfile for studentId " + studentId));
    }

    public EProfile updateSfiaScores(UUID studentId, SfiaScores sfiaScores) {
        EProfile eProfile = getByStudentId(studentId);
        eProfile.updateSfiaScores(sfiaScores);
        return eProfileRepository.save(eProfile);
    }

    public EProfile updateProjectCompletionRate(UUID studentId, float rate) {
        EProfile eProfile = getByStudentId(studentId);
        eProfile.updateProjectCompletionRate(rate);
        return eProfileRepository.save(eProfile);
    }

    public EProfile updateConsistencyScore(UUID studentId, float score) {
        EProfile eProfile = getByStudentId(studentId);
        eProfile.updateConsistencyScore(score);
        return eProfileRepository.save(eProfile);
    }

    public EProfile updateThinkingAssessment(UUID studentId, LevelOfThinking levelOfThinking,
                                              NoveltyOfThinking noveltyOfThinking) {
        EProfile eProfile = getByStudentId(studentId);
        eProfile.updateThinkingAssessment(levelOfThinking, noveltyOfThinking);
        return eProfileRepository.save(eProfile);
    }
}
