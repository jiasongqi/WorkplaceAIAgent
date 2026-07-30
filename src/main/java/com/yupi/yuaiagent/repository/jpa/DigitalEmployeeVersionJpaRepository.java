package com.yupi.yuaiagent.repository.jpa;

import com.yupi.yuaiagent.repository.entity.DigitalEmployeeVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DigitalEmployeeVersionJpaRepository extends JpaRepository<DigitalEmployeeVersionEntity, Long> {

    Optional<DigitalEmployeeVersionEntity> findByEmployeeIdAndConfigVersion(String employeeId, Integer configVersion);
}
