package com.yupi.yuaiagent.artifact.adoption;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArtifactAdoptionRepository extends JpaRepository<ArtifactAdoption, Long> {

    Optional<ArtifactAdoption> findByArtifactIdAndTurnIdAndStage(
            String artifactId, String turnId, ArtifactAdoptionStage stage);

    List<ArtifactAdoption> findByArtifactIdOrderByCreatedAtDesc(String artifactId);

    long countByArtifactIdAndStage(String artifactId, ArtifactAdoptionStage stage);

    @Query("""
            select a.artifactId as artifactId, count(a) as total
            from ArtifactAdoption a
            where a.artifactId in :artifactIds and a.stage = :stage
            group by a.artifactId
            """)
    List<AdoptionCount> countByArtifactIdsAndStage(
            @Param("artifactIds") List<String> artifactIds,
            @Param("stage") ArtifactAdoptionStage stage);

    interface AdoptionCount {
        String getArtifactId();

        long getTotal();
    }
}
