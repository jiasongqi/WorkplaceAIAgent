package com.yupi.yuaiagent.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Feedback Repository — file-based persistence for user feedback.
 *
 * @author jsq
 */
@Slf4j
@Repository
public class FeedbackRepository {

    @Value("${feedback.storage.dir:./tmp/feedback}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final List<Feedback> feedbackList = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        File dir = new File(storageDir);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "feedback.json");
        if (file.exists()) {
            try {
                List<Feedback> loaded = objectMapper.readValue(file,
                        new TypeReference<List<Feedback>>() {});
                feedbackList.addAll(loaded);
                log.info("[FeedbackRepository] Loaded {} feedback entries", loaded.size());
            } catch (IOException e) {
                log.warn("[FeedbackRepository] Failed to load feedback: {}", e.getMessage());
            }
        }
    }

    public void save(Feedback feedback) {
        feedbackList.add(feedback);
        persist();
    }

    public List<Feedback> findAll() {
        return Collections.unmodifiableList(feedbackList);
    }

    public List<Feedback> findByUserId(String userId) {
        return feedbackList.stream()
                .filter(f -> f.userId().equals(userId))
                .toList();
    }

    public List<Feedback> findByAgentType(String agentType) {
        return feedbackList.stream()
                .filter(f -> f.agentType().equals(agentType))
                .toList();
    }

    public long countByRating(Feedback.Rating rating) {
        return feedbackList.stream()
                .filter(f -> f.rating() == rating)
                .count();
    }

    public double getApprovalRate() {
        if (feedbackList.isEmpty()) return -1.0;
        long up = countByRating(Feedback.Rating.UP);
        return (double) up / feedbackList.size();
    }

    public double getAgentApprovalRate(String agentType) {
        List<Feedback> agentFeedback = findByAgentType(agentType);
        if (agentFeedback.isEmpty()) return -1.0;
        long up = agentFeedback.stream()
                .filter(f -> f.rating() == Feedback.Rating.UP).count();
        return (double) up / agentFeedback.size();
    }

    private void persist() {
        try {
            File file = new File(storageDir, "feedback.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, feedbackList);
        } catch (IOException e) {
            log.error("[FeedbackRepository] Failed to persist feedback", e);
        }
    }
}
