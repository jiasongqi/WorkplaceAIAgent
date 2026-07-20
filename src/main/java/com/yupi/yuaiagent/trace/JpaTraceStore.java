package com.yupi.yuaiagent.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.repository.entity.TraceEntity;
import com.yupi.yuaiagent.repository.jpa.TraceJpaRepository;
import com.yupi.yuaiagent.trace.model.ExecutionTrace;
import com.yupi.yuaiagent.trace.model.TraceStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA-backed trace store ({@code app.storage.type=jdbc}).
 * Stores the full {@link ExecutionTrace} JSON in metadata for fidelity.
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "app.storage.type", havingValue = "jdbc")
public class JpaTraceStore implements TraceStore {

    private final TraceJpaRepository jpaRepo;
    private final ObjectMapper objectMapper;
    private final TraceProperties traceProperties;

    public JpaTraceStore(TraceJpaRepository jpaRepo, TraceProperties traceProperties) {
        this.jpaRepo = jpaRepo;
        this.traceProperties = traceProperties;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    @Transactional
    public ExecutionTrace save(ExecutionTrace trace) {
        TraceEntity entity = jpaRepo.findByTraceId(trace.getTraceId()).orElseGet(TraceEntity::new);
        entity.setTraceId(trace.getTraceId());
        entity.setUserId(trace.getUserId() != null ? trace.getUserId() : "");
        entity.setConversationId(trace.getChatId());
        entity.setStatus(trace.getStatus() != null ? trace.getStatus().name() : TraceStatus.RUNNING.name());
        entity.setStartedAt(toOffset(trace.getStartTime()));
        if (trace.getEndTime() != null) {
            entity.setCompletedAt(toOffset(trace.getEndTime()));
            if (trace.getStartTime() != null) {
                entity.setTotalMs((int) (trace.getEndTime().toEpochMilli() - trace.getStartTime().toEpochMilli()));
            }
        }
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("_traceJson", objectMapper.writeValueAsString(trace));
            meta.put("requestId", trace.getRequestId());
            entity.setMetadata(meta);
            if (trace.getSpans() != null) {
                entity.setSpans(objectMapper.convertValue(trace.getSpans(), new TypeReference<>() {}));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trace: " + e.getMessage(), e);
        }
        jpaRepo.save(entity);
        return trace;
    }

    @Override
    public Optional<ExecutionTrace> findById(String traceId) {
        return jpaRepo.findByTraceId(traceId).map(this::toDomain);
    }

    @Override
    public List<ExecutionTrace> findByChatId(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return List.of();
        }
        return jpaRepo.findByConversationId(chatId).stream()
                .map(this::toDomain)
                .sorted(Comparator.comparing(ExecutionTrace::getStartTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionTrace> findByChatId(String chatId, int pageNum, int pageSize) {
        return paginate(findByChatId(chatId), pageNum, pageSize);
    }

    @Override
    public List<ExecutionTrace> findByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return jpaRepo.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionTrace> findByUserId(String userId, int pageNum, int pageSize) {
        return paginate(findByUserId(userId), pageNum, pageSize);
    }

    @Override
    @Transactional
    public void enforceRetentionPolicy(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        int max = traceProperties.getMaxTracesPerUser();
        List<TraceEntity> list = jpaRepo.findByUserIdOrderByStartedAtDesc(userId);
        if (list.size() <= max) {
            return;
        }
        List<TraceEntity> drop = list.subList(max, list.size());
        jpaRepo.deleteAll(drop);
        log.info("[trace:jdbc] retention userId={}, removed={}", userId, drop.size());
    }

    @Override
    public int count() {
        return (int) jpaRepo.count();
    }

    private ExecutionTrace toDomain(TraceEntity entity) {
        try {
            Object json = entity.getMetadata() != null ? entity.getMetadata().get("_traceJson") : null;
            if (json != null) {
                return objectMapper.readValue(String.valueOf(json), ExecutionTrace.class);
            }
        } catch (Exception e) {
            log.warn("[trace:jdbc] deserialize failed traceId={}: {}", entity.getTraceId(), e.getMessage());
        }
        throw new IllegalStateException("trace payload missing for " + entity.getTraceId());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? OffsetDateTime.now() : instant.atOffset(ZoneOffset.UTC);
    }

    private static <T> List<T> paginate(List<T> full, int pageNum, int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;
        int from = (pageNum - 1) * pageSize;
        if (from >= full.size()) {
            return List.of();
        }
        return full.subList(from, Math.min(from + pageSize, full.size()));
    }
}
