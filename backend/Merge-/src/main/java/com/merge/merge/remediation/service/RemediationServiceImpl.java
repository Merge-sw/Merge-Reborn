package com.merge.merge.remediation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merge.merge.ai.service.InstructorService;
import com.merge.merge.identity.models.Context;
import com.merge.merge.identity.service.ContextService;
import com.merge.merge.remediation.models.AttemptHistoryEntry;
import com.merge.merge.remediation.models.Mission;
import com.merge.merge.remediation.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationServiceImpl implements RemediationService {

    private final MissionRepository missionRepository;
    private final InstructorService instructorService;
    private final ContextService contextService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Override
    public void handleFailure(UUID studentId, UUID conceptId, String failureSource, Map<String, Object> attemptData) {
        log.info("Handling failure flow for student: {}, concept: {}, source: {}", studentId, conceptId, failureSource);

        // 1. Fetch the student's existing open (passed: false) Missions for this conceptId.
        List<Mission> openMissions = missionRepository.findByStudentIdAndConceptIdAndPassed(studentId, conceptId, false);

        // 2. Build the generation input:
        Map<String, Object> payload = new HashMap<>();
        payload.put("flowType", "FAILURE");
        payload.put("studentId", studentId.toString());
        payload.put("conceptId", conceptId.toString());
        payload.put("failureSource", failureSource);
        payload.put("attemptData", attemptData);

        List<Map<String, Object>> existingOpenMissionsList = new ArrayList<>();
        for (Mission m : openMissions) {
            Map<String, Object> missionMap = new HashMap<>();
            missionMap.put("missionId", m.getId().toString());
            missionMap.put("painPointDescription", m.getPainPointDescription());

            List<Map<String, Object>> historyList = new ArrayList<>();
            if (m.getAttemptHistory() != null) {
                for (AttemptHistoryEntry entry : m.getAttemptHistory()) {
                    Map<String, Object> histEntry = new HashMap<>();
                    histEntry.put("attemptData", entry.getAttemptData());
                    histEntry.put("generatedAt", entry.getGeneratedAt().toString());
                    historyList.add(histEntry);
                }
            }
            missionMap.put("attemptHistory", historyList);
            existingOpenMissionsList.add(missionMap);
        }
        payload.put("existingOpenMissions", existingOpenMissionsList);

        // Fetch opaque Context personalisedData
        Object personalisedData = null;
        try {
            Context context = contextService.getByStudentId(studentId);
            if (context != null) {
                personalisedData = context.getPersonalisedData();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve Context personalisedData for student {}: {}", studentId, e.getMessage());
        }
        payload.put("personalisedData", personalisedData);

        // 3. Send payload as one single combined prompt to AI Orchestration
        instructorService.missionGenerate(studentId, conceptId, payload);
    }

    @Override
    public void handlePass(UUID studentId, UUID conceptId, String source, Map<String, Object> attemptData) {
        log.info("Handling pass flow for student: {}, concept: {}, source: {}", studentId, conceptId, source);

        // 1. Fetch the student's existing open (passed: false) Missions for this conceptId.
        List<Mission> openMissions = missionRepository.findByStudentIdAndConceptIdAndPassed(studentId, conceptId, false);
        if (openMissions.isEmpty()) {
            log.info("No open missions to resolve for student: {}, concept: {}", studentId, conceptId);
            return;
        }

        // 2. Build the generation input:
        Map<String, Object> payload = new HashMap<>();
        payload.put("flowType", "RESOLUTION");
        payload.put("studentId", studentId.toString());
        payload.put("conceptId", conceptId.toString());
        payload.put("source", source);
        payload.put("attemptData", attemptData);

        List<Map<String, Object>> existingOpenMissionsList = new ArrayList<>();
        for (Mission m : openMissions) {
            Map<String, Object> missionMap = new HashMap<>();
            missionMap.put("missionId", m.getId().toString());
            missionMap.put("painPointDescription", m.getPainPointDescription());

            List<Map<String, Object>> historyList = new ArrayList<>();
            if (m.getAttemptHistory() != null) {
                for (AttemptHistoryEntry entry : m.getAttemptHistory()) {
                    Map<String, Object> histEntry = new HashMap<>();
                    histEntry.put("attemptData", entry.getAttemptData());
                    histEntry.put("generatedAt", entry.getGeneratedAt().toString());
                    historyList.add(histEntry);
                }
            }
            missionMap.put("attemptHistory", historyList);
            existingOpenMissionsList.add(missionMap);
        }
        payload.put("existingOpenMissions", existingOpenMissionsList);

        // Fetch opaque Context personalisedData
        Object personalisedData = null;
        try {
            Context context = contextService.getByStudentId(studentId);
            if (context != null) {
                personalisedData = context.getPersonalisedData();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve Context personalisedData for student {}: {}", studentId, e.getMessage());
        }
        payload.put("personalisedData", personalisedData);

        // 3. Send payload as one single combined prompt to AI Orchestration
        instructorService.missionGenerate(studentId, conceptId, payload);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMissionGenerationResult(UUID jobId, UUID studentId, UUID conceptId, String llmResult, Map<String, Object> originalContext) {
        String flowType = (String) originalContext.get("flowType");
        Instant now = Instant.now();

        if ("RESOLUTION".equals(flowType)) {
            try {
                String sanitized = sanitizeJson(llmResult);
                Map<String, Object> parsed = objectMapper.readValue(sanitized, new TypeReference<Map<String, Object>>() {});
                List<String> resolvedIdsStr = (List<String>) parsed.get("resolvedMissionIds");
                if (resolvedIdsStr != null) {
                    for (String idStr : resolvedIdsStr) {
                        try {
                            UUID missionId = UUID.fromString(idStr);
                            Optional<Mission> optionalMission = missionRepository.findById(missionId);
                            if (optionalMission.isPresent()) {
                                Mission mission = optionalMission.get();
                                if (!mission.isPassed()) {
                                    mission.setPassed(true);
                                    mission.setUpdatedAt(now);
                                    missionRepository.save(mission);
                                    log.info("Mission {} marked as resolved/passed", missionId);
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            // ignore/null
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse resolution LLM result for job " + jobId + ": " + llmResult, e);
            }
            return;
        }

        if (!"FAILURE".equals(flowType)) {
            return;
        }

        Map<String, Object> attemptData = (Map<String, Object>) originalContext.get("attemptData");

        try {
            String sanitized = sanitizeJson(llmResult);
            List<Map<String, Object>> parsed = objectMapper.readValue(sanitized, new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> entry : parsed) {
                String painPointDescription = (String) entry.get("painPointDescription");
                String matchedMissionIdStr = (String) entry.get("matchedMissionId");
                String conceptAndContext = (String) entry.get("conceptAndContext");

                UUID matchedId = null;
                if (matchedMissionIdStr != null && !matchedMissionIdStr.trim().isEmpty() && !"null".equalsIgnoreCase(matchedMissionIdStr)) {
                    try {
                        matchedId = UUID.fromString(matchedMissionIdStr);
                    } catch (IllegalArgumentException e) {
                        // ignore/null
                    }
                }

                if (matchedId != null) {
                    Optional<Mission> optionalMission = missionRepository.findById(matchedId);
                    if (optionalMission.isPresent()) {
                        Mission mission = optionalMission.get();

                        if (mission.getAttemptHistory() == null) {
                            mission.setAttemptHistory(new ArrayList<>());
                        }
                        mission.getAttemptHistory().add(AttemptHistoryEntry.builder()
                                .attemptData(attemptData)
                                .generatedAt(now)
                                .build());

                        mission.setConceptAndContext(conceptAndContext);
                        mission.setUpdatedAt(now);

                        missionRepository.save(mission);
                        log.info("Updated existing Mission {} with new attempt and advice", matchedId);
                        continue;
                    }
                }

                // New mission or mismatch -> create new
                Mission newMission = Mission.builder()
                        .id(UUID.randomUUID())
                        .conceptId(conceptId)
                        .studentId(studentId)
                        .painPointDescription(painPointDescription != null ? painPointDescription : "Unknown pain point")
                        .conceptAndContext(conceptAndContext != null ? conceptAndContext : "No advice generated")
                        .attemptHistory(new ArrayList<>(List.of(
                                AttemptHistoryEntry.builder()
                                        .attemptData(attemptData)
                                        .generatedAt(now)
                                        .build()
                        )))
                        .passed(false)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                missionRepository.save(newMission);
                log.info("Created new Mission {} for pain point: {}", newMission.getId(), painPointDescription);
            }

        } catch (Exception e) {
            log.error("Failed to parse LLM result for job " + jobId + ": " + llmResult, e);
        }
    }

    private String sanitizeJson(String json) {
        if (json == null) return "[]";
        json = json.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }
}
