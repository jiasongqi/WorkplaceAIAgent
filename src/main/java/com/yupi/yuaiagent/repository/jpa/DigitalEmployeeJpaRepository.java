package com.yupi.yuaiagent.repository.jpa;

import com.yupi.yuaiagent.repository.entity.DigitalEmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DigitalEmployeeJpaRepository extends JpaRepository<DigitalEmployeeEntity, Long> {

    Optional<DigitalEmployeeEntity> findByEmployeeId(String employeeId);

    List<DigitalEmployeeEntity> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId);

    Optional<DigitalEmployeeEntity> findFirstByOwnerUserIdAndActiveTrue(String ownerUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DigitalEmployeeEntity e set e.active = false where e.ownerUserId = :ownerUserId")
    int deactivateAll(@Param("ownerUserId") String ownerUserId);
}
