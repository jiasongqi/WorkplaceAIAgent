package com.yupi.yuaiagent.artifact;

import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;

import java.util.Optional;

/**
 * Idempotent publishing entry point for structured artifacts.
 */
public class ArtifactPublisher {

    private final ArtifactShelf artifactShelf;
    private final ArtifactPublishPolicy publishPolicy;

    public ArtifactPublisher(ArtifactShelf artifactShelf, ArtifactPublishPolicy publishPolicy) {
        this.artifactShelf = artifactShelf;
        this.publishPolicy = publishPolicy;
    }

    public ArtifactShelf.PutResult publish(Artifact draft, String sourceTraceId) {
        ArtifactPublishPolicy.Decision decision = publishPolicy.evaluate(draft, sourceTraceId);
        if (!decision.accepted()) {
            return ArtifactShelf.PutResult.fail(decision.reason());
        }

        Artifact artifact = decision.artifact();
        artifact.setStatus(ArtifactStatus.PUBLISHED);
        Optional<Artifact> existing = artifactShelf.findByDedupKey(artifact.getDedupKey());
        if (existing.isPresent()) {
            return ArtifactShelf.PutResult.ok(existing.get());
        }
        try {
            return artifactShelf.put(artifact);
        } catch (RuntimeException e) {
            Optional<Artifact> raced = artifactShelf.findByDedupKey(artifact.getDedupKey());
            if (raced.isPresent()) {
                return ArtifactShelf.PutResult.ok(raced.get());
            }
            throw e;
        }
    }
}
