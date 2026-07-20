package com.yupi.yuaiagent.repository.jpa;

import com.yupi.yuaiagent.repository.entity.McpAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface McpAuditLogJpaRepository extends JpaRepository<McpAuditLogEntity, Long> {

    List<McpAuditLogEntity> findByToolName(String toolName);

    List<McpAuditLogEntity> findByServerId(String serverId);

    List<McpAuditLogEntity> findByCreatedAtAfter(OffsetDateTime since);
}
