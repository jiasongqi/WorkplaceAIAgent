package com.yupi.yuaiagent.artifact.adoption;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Idempotent OFFERED/ADOPTED ledger. It never changes Artifact status.
 */
@Service
public class ArtifactAdoptionService {

    private final ArtifactAdoptionRepository repository;

    public ArtifactAdoptionService(ArtifactAdoptionRepository repository) {
        this.repository = repository;
    }

    public List<ArtifactAdoption> recordOffered(List<String> artifactIds, String consumerAgent,
                                                String chatId, String turnId) {
        return record(artifactIds, consumerAgent, chatId, turnId,
                ArtifactAdoptionStage.OFFERED, null, null);
    }

    public List<ArtifactAdoption> recordAdopted(List<String> artifactIds, String consumerAgent,
                                                String chatId, String turnId,
                                                Double confidence, String evidence) {
        return record(artifactIds, consumerAgent, chatId, turnId,
                ArtifactAdoptionStage.ADOPTED, confidence, evidence);
    }

    public List<ArtifactAdoption> offer(List<String> artifactIds, String consumerAgent,
                                       String chatId, String turnId) {
        return recordOffered(artifactIds, consumerAgent, chatId, turnId);
    }

    public List<ArtifactAdoption> adopt(List<String> artifactIds, String consumerAgent,
                                       String chatId, String turnId,
                                       Double confidence, String evidence) {
        return recordAdopted(artifactIds, consumerAgent, chatId, turnId, confidence, evidence);
    }

    public long offeredCount(String artifactId) {
        return repository.countByArtifactIdAndStage(artifactId, ArtifactAdoptionStage.OFFERED);
    }

    public long adoptedCount(String artifactId) {
        return repository.countByArtifactIdAndStage(artifactId, ArtifactAdoptionStage.ADOPTED);
    }

    public Map<String, Long> offeredCounts(List<String> artifactIds) {
        return counts(artifactIds, ArtifactAdoptionStage.OFFERED);
    }

    public Map<String, Long> adoptedCounts(List<String> artifactIds) {
        return counts(artifactIds, ArtifactAdoptionStage.ADOPTED);
    }

    private Map<String, Long> counts(List<String> artifactIds, ArtifactAdoptionStage stage) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return Map.of();
        }
        return repository.countByArtifactIdsAndStage(artifactIds, stage).stream()
                .collect(Collectors.toUnmodifiableMap(
                        ArtifactAdoptionRepository.AdoptionCount::getArtifactId,
                        ArtifactAdoptionRepository.AdoptionCount::getTotal));
    }

    private List<ArtifactAdoption> record(List<String> artifactIds, String consumerAgent,
                                          String chatId, String turnId,
                                          ArtifactAdoptionStage stage,
                                          Double confidence, String evidence) {
        if (artifactIds == null || artifactIds.isEmpty() || turnId == null || turnId.isBlank()) {
            return List.of();
        }
        List<ArtifactAdoption> result = new ArrayList<>();
        for (String artifactId : artifactIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList()) {
            ArtifactAdoption existing = repository
                    .findByArtifactIdAndTurnIdAndStage(artifactId, turnId, stage)
                    .orElse(null);
            if (existing != null) {
                result.add(existing);
                continue;
            }
            ArtifactAdoption adoption = new ArtifactAdoption();
            adoption.setArtifactId(artifactId);
            adoption.setConsumerAgent(
                    consumerAgent == null || consumerAgent.isBlank() ? "GENERAL" : consumerAgent);
            adoption.setChatId(chatId);
            adoption.setTurnId(turnId);
            adoption.setStage(stage);
            adoption.setConfidence(confidence);
            adoption.setEvidence(evidence);
            try {
                result.add(repository.save(adoption));
            } catch (DataIntegrityViolationException race) {
                repository.findByArtifactIdAndTurnIdAndStage(artifactId, turnId, stage)
                        .ifPresent(result::add);
            }
        }
        return List.copyOf(result);
    }
}
