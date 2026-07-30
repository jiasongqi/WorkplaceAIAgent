package com.yupi.yuaiagent.repository.jpa;

import com.yupi.yuaiagent.repository.entity.UserCompanionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCompanionJpaRepository extends JpaRepository<UserCompanionEntity, Long> {
    Optional<UserCompanionEntity> findByUserId(String userId);
}
