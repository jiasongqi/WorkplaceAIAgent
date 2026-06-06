package com.yupi.yuaiagent.usage;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Usage tracker — records events and provides analytics.
 * File-based append-only storage.
 *
 * @author jsq
 */
@Slf4j
@Service
public class UsageTracker {

    @Value("${artifact.storage.dir:./tmp/artifacts}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final List<UsageEvent> events = new CopyOnWriteArrayList<>();
    private File storageFile;

    @PostConstruct
    public void init() {
        File dir = new File(storageDir);
        if (!dir.exists()) dir.mkdirs();
        storageFile = new File(dir, "usage-events.json");
        loadFromFile();
        log.info("[usage] tracker initialized, events: {}", events.size());
    }

    /**
     * Records a usage event.
     */
    public UsageEvent track(String userId, UsageEventType type, String agentType, long durationMs) {
        UsageEvent event = new UsageEvent();
        event.setEventId(IdUtil.fastSimpleUUID());
        event.setUserId(userId);
        event.setType(type);
        event.setAgentType(agentType);
        event.setDurationMs(durationMs);
        event.setTimestamp(LocalDateTime.now());

        events.add(event);
        saveToFile();
        return event;
    }

    /**
     * Returns usage stats for a user.
     */
    public UsageStats getStats(String userId) {
        List<UsageEvent> userEvents = events.stream()
                .filter(e -> userId.equals(e.getUserId()))
                .toList();

        UsageStats stats = new UsageStats();
        stats.setTotalEvents(userEvents.size());

        // Count by type
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (UsageEventType t : UsageEventType.values()) {
            int count = (int) userEvents.stream().filter(e -> e.getType() == t).count();
            if (count > 0) byType.put(t.getDisplayName(), count);
        }
        stats.setEventsByType(byType);

        // Count by agent
        Map<String, Integer> byAgent = userEvents.stream()
                .filter(e -> e.getAgentType() != null)
                .collect(Collectors.groupingBy(UsageEvent::getAgentType, Collectors.summingInt(e -> 1)));
        stats.setEventsByAgent(byAgent);

        // Daily counts (last 7 days)
        Map<String, Integer> dailyCounts = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            int count = (int) userEvents.stream()
                    .filter(e -> e.getTimestamp() != null && e.getTimestamp().toLocalDate().equals(date))
                    .count();
            dailyCounts.put(date.toString(), count);
        }
        stats.setDailyCounts(dailyCounts);

        // Total duration
        long totalMs = userEvents.stream().mapToLong(UsageEvent::getDurationMs).sum();
        stats.setTotalDurationMs(totalMs);

        return stats;
    }

    // ─── File I/O ───

    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                List<UsageEvent> loaded = objectMapper.readValue(storageFile,
                        new TypeReference<List<UsageEvent>>() {});
                events.addAll(loaded);
            } catch (IOException e) {
                log.error("[usage] failed to load file", e);
            }
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, events);
        } catch (IOException e) {
            log.error("[usage] failed to save file", e);
        }
    }

    @Data
    public static class UsageStats {
        private int totalEvents;
        private Map<String, Integer> eventsByType;
        private Map<String, Integer> eventsByAgent;
        private Map<String, Integer> dailyCounts;
        private long totalDurationMs;
    }
}
