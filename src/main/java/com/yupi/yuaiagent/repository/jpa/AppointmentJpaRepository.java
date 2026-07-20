package com.yupi.yuaiagent.repository.jpa;

import java.util.List;
import java.util.Optional;
import com.yupi.yuaiagent.repository.entity.AppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, Long> {

    Optional<AppointmentEntity> findByAppointmentId(String appointmentId);

    List<AppointmentEntity> findByUserIdOrderByScheduledAtDesc(String userId);
}
