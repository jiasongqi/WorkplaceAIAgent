package com.yupi.yuaiagent.repository.jpa;

import com.yupi.yuaiagent.repository.entity.ReflexionMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ReflexionMemoryJpaRepository extends JpaRepository<ReflexionMemoryEntity, Long> {

    List<ReflexionMemoryEntity> findByUserId(String userId);

    List<ReflexionMemoryEntity> findByUserIdOrUserIdIsNull(String userId);

    List<ReflexionMemoryEntity> findByUserIdIsNull();

    void deleteByExpiresAtBefore(OffsetDateTime now);
}
