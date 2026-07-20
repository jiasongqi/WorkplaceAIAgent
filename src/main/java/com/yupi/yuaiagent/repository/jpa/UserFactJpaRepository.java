package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.util.Optional;
import com.yupi.yuaiagent.repository.entity.UserFactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFactJpaRepository extends JpaRepository<UserFactEntity, Long> {

    List<UserFactEntity> findByUserId(String userId);

    Optional<UserFactEntity> findByUserIdAndFactKey(String userId, String factKey);

    void deleteByUserId(String userId);
}
