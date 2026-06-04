package com.yupi.yuaiagent.quality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quality review persistence — only stores HIGH and CRITICAL risk reviews
 * for audit/alerting purposes. Normal reviews are recorded in ExecutionTrace only.
 *
 * @author jsq
 */
@Slf4j
@Repository
public class QualityReviewRepository {

    @Value("${artifact.storage.dir:./tmp/artifacts}")
    private String storageDir;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final List<QualityReview> reviews = new CopyOnWriteArrayList<>();
    private File storageFile;

    @PostConstruct
    public void init() {
        File dir = new File(storageDir);
        if (!dir.exists()) dir.mkdirs();
        storageFile = new File(dir, "quality-reviews.json");
        loadFromFile();
        log.info("[quality-review] repository initialized, reviews: {}", reviews.size());
    }

    /**
     * Persists a review only if risk level is HIGH or CRITICAL.
     */
    public void saveIfHighRisk(QualityReview review) {
        if (review.getRiskLevel() == RiskLevel.HIGH || review.getRiskLevel() == RiskLevel.CRITICAL) {
            reviews.add(review);
            saveToFile();
            log.info("[quality-review] persisted HIGH/CRITICAL review: id={}, risk={}, overall={}",
                    review.getReviewId(), review.getRiskLevel(), review.getOverallScore());
        }
    }

    public List<QualityReview> findAll() {
        return new ArrayList<>(reviews);
    }

    public List<QualityReview> findByRiskLevel(RiskLevel level) {
        return reviews.stream()
                .filter(r -> r.getRiskLevel() == level)
                .toList();
    }

    // ─── File I/O ───

    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                List<QualityReview> loaded = objectMapper.readValue(storageFile,
                        new TypeReference<List<QualityReview>>() {});
                reviews.addAll(loaded);
            } catch (IOException e) {
                log.error("[quality-review] failed to load file", e);
            }
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, reviews);
        } catch (IOException e) {
            log.error("[quality-review] failed to save file", e);
        }
    }
}
