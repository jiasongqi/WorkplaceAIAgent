package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.util.Optional;
import com.yupi.yuaiagent.repository.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtifactJpaRepository extends JpaRepository<ArtifactEntity, Long>,
        JpaSpecificationExecutor<ArtifactEntity> {

    Optional<ArtifactEntity> findByArtifactId(String artifactId);

    List<ArtifactEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<ArtifactEntity> findByDedupKey(String dedupKey);
}
