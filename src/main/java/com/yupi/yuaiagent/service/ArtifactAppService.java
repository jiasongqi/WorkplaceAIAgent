package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.adoption.ArtifactAdoptionService;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactSummary;
import com.yupi.yuaiagent.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * User-facing artifact queries (ownership-scoped). Admin list remains on ArtifactController.
 */
@Service
public class ArtifactAppService {

    @Resource
    private ArtifactShelf artifactShelf;

    @Resource
    private ArtifactAdoptionService artifactAdoptionService;

    public List<ArtifactSummary> listMine(String userId, String chatId) {
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.notLoggedIn();
        }
        ArtifactQuery.ArtifactQueryBuilder builder = ArtifactQuery.builder().userId(userId);
        if (StringUtils.hasText(chatId)) {
            builder.chatId(chatId);
        }
        List<Artifact> artifacts = artifactShelf.query(builder.build());
        List<String> artifactIds = artifacts.stream().map(Artifact::getArtifactId).toList();
        var offeredCounts = artifactAdoptionService.offeredCounts(artifactIds);
        var adoptedCounts = artifactAdoptionService.adoptedCounts(artifactIds);
        return artifacts.stream()
                .map(artifact -> ArtifactSummary.from(
                        artifact,
                        offeredCounts.getOrDefault(artifact.getArtifactId(), 0L),
                        adoptedCounts.getOrDefault(artifact.getArtifactId(), 0L)))
                .toList();
    }

    public Artifact getMine(String userId, String artifactId) {
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.notLoggedIn();
        }
        Artifact artifact = artifactShelf.get(artifactId)
                .orElseThrow(() -> BusinessException.notFound("交付物"));
        if (!userId.equals(artifact.getUserId())) {
            throw BusinessException.forbidden();
        }
        return artifact;
    }
}
