package com.yupi.yuaiagent.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.yupi.yuaiagent.message.PersistentChatMessage;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.session.SessionManager;
import com.yupi.yuaiagent.session.SessionManager.SessionInfo;

import jakarta.annotation.Resource;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat search service — weighted scoring across multiple areas.
 * <p>
 * Scoring model:
 * <ul>
 *   <li>Area weights: TITLE=100, USER_MESSAGE=30, AI_MESSAGE=20</li>
 *   <li>Match types: equals=100, startsWith=70, contains=50</li>
 *   <li>Recency bonus: ≤1d=30, ≤7d=20, ≤30d=10</li>
 *   <li>Hit count bonus: count × 10</li>
 * </ul>
 * <p>
 * Current scale (100 sessions × 100 messages = 10K): direct scan, millisecond-level.
 * Future: Lucene at 10K+ sessions, ElasticSearch at 100K+.
 *
 * @author jsq
 */
@Slf4j
@Service
public class ChatSearchService {

    @Resource
    private SessionManager sessionManager;

    @Resource
    private PersistentMessageRepository messageRepository;

    /**
     * Searches across all sessions for a user.
     *
     * @param keyword the search keyword (case-insensitive)
     * @param userId  the user ID (for session scoping)
     * @return results sorted by relevance (highest first)
     */
    public List<SessionSearchResult> search(String keyword, String userId) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        String lowerKeyword = keyword.toLowerCase().trim();
        List<SessionInfo> sessions = sessionManager.getSessionsByStatus(
                userId, com.yupi.yuaiagent.session.SessionStatus.ACTIVE);

        List<SessionSearchResult> results = new ArrayList<>();

        for (SessionInfo session : sessions) {
            double score = 0;
            SearchHit bestHit = null;
            String bestSnippet = null;
            int hitCount = 0;

            // Layer 1: Title weight (highest)
            int titleMatch = calculateMatchScore(session.getTitle(), lowerKeyword);
            if (titleMatch > 0) {
                score += 100.0 * titleMatch / 100.0;
            }

            // Layer 2 & 3: Message content weight
            List<PersistentChatMessage> messages = messageRepository.findByChatId(session.getChatId());
            for (PersistentChatMessage msg : messages) {
                int matchScore = calculateMatchScore(msg.getContent(), lowerKeyword);
                if (matchScore <= 0) continue;

                hitCount++;

                // Area weight
                double areaWeight = "user".equals(msg.getRole()) ? 30.0 : 20.0;
                score += areaWeight * matchScore / 100.0;

                // Record best hit for scroll-to-highlight
                if (bestHit == null) {
                    int offset = msg.getContent().toLowerCase().indexOf(lowerKeyword);
                    bestHit = new SearchHit(msg.getMessageId(), offset, lowerKeyword.length());
                    bestSnippet = extractSnippet(msg.getContent(), keyword);
                }
            }

            // Hit count bonus
            score += hitCount * 10.0;

            // Layer 4: Recency bonus
            if (session.getLastActiveAt() != null) {
                long days = ChronoUnit.DAYS.between(session.getLastActiveAt().toLocalDate(), LocalDate.now());
                if (days <= 1) score += 30;
                else if (days <= 7) score += 20;
                else if (days <= 30) score += 10;
            }

            if (score > 0) {
                // Normalize to 0-100
                int relevance = (int) Math.min(100, score);
                results.add(new SessionSearchResult(
                        session.getChatId(),
                        session.getTitle(),
                        relevance,
                        bestSnippet,
                        bestHit,
                        session.getLastActiveAt() != null ? session.getLastActiveAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null
                ));
            }
        }

        // Sort by relevance descending
        results.sort(Comparator.comparingInt(SessionSearchResult::relevance).reversed());
        return results;
    }

    // ─── Match scoring ───

    /**
     * Three-layer match scoring: equals(100) > startsWith(70) > contains(50).
     * Order matters: startsWith must be checked before contains.
     */
    static int calculateMatchScore(String content, String lowerKeyword) {
        if (content == null || content.isEmpty()) return 0;
        String lowerContent = content.toLowerCase();

        // 1. Exact match (highest)
        if (lowerContent.equals(lowerKeyword)) return 100;

        // 2. Prefix match (second)
        if (lowerContent.startsWith(lowerKeyword)) return 70;

        // 3. Contains match (third)
        if (lowerContent.contains(lowerKeyword)) return 50;

        return 0;
    }

    /**
     * Extracts a snippet around the keyword match (50 chars before and after).
     */
    private String extractSnippet(String content, String keyword) {
        int idx = content.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return content.substring(0, Math.min(100, content.length()));
        }
        int start = Math.max(0, idx - 50);
        int end = Math.min(content.length(), idx + keyword.length() + 50);
        return (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
    }

    // ─── Result types ───

    public record SessionSearchResult(
            String chatId,
            String title,
            int relevance,
            String snippet,
            SearchHit bestHit,
            Long timestamp
    ) {}

    public record SearchHit(
            String messageId,
            int offset,
            int length
    ) {}
}
