package com.yupi.yuaiagent.nlu;

import com.yupi.yuaiagent.nlu.NluContext.AliasMatch;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Alias metadata extractor — scans user input for known aliases.
 * Output: List of AliasMatch — pure metadata, no text modification, no state mutation.
 *
 * <p>Short aliases (<=2 chars) use word-boundary matching to avoid false positives.
 * Chinese aliases use rear-boundary only ({@code (?!\p{IsHan})}) because Chinese
 * has no spaces between words — front boundary is unreliable.
 *
 * <p>For production with thousands of aliases, upgrade to Aho-Corasick or Trie.
 *
 * @author jsq
 */
@Slf4j
@Component
public class AliasResolver {

    private final Map<String, String> aliasMap = new ConcurrentHashMap<>();
    private final Map<String, String> typeMap = new ConcurrentHashMap<>();
    private final Map<String, Pattern> shortAliasPatterns = new ConcurrentHashMap<>();

    private static final int SHORT_ALIAS_THRESHOLD = 2;

    @PostConstruct
    public void init() {
        // Business domain aliases — extend via DB in Phase 2
        register("TX", "腾讯资方", "ADVERTISER");
        register("BD", "百度资方", "ADVERTISER");
        register("KS", "快手资方", "ADVERTISER");
        register("DY", "抖音资方", "ADVERTISER");
        register("腾讯", "腾讯资方", "ADVERTISER");
        register("Tencent", "腾讯资方", "ADVERTISER");
    }

    public void register(String alias, String canonical, String type) {
        String lower = alias.toLowerCase();
        aliasMap.put(lower, canonical);
        typeMap.put(canonical, type);

        if (alias.length() <= SHORT_ALIAS_THRESHOLD) {
            shortAliasPatterns.put(lower, buildWordBoundaryPattern(alias));
        }
    }

    /**
     * Scan text for known aliases. Returns matches as metadata only — does NOT modify text.
     */
    public List<AliasMatch> resolve(String text) {
        String lower = text.toLowerCase();
        List<AliasMatch> matches = new ArrayList<>();

        aliasMap.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
            .forEach(entry -> {
                String alias = entry.getKey();
                boolean matched;

                if (alias.length() <= SHORT_ALIAS_THRESHOLD) {
                    Pattern pattern = shortAliasPatterns.get(alias);
                    matched = pattern != null && pattern.matcher(lower).find();
                } else {
                    matched = lower.contains(alias);
                }

                if (matched) {
                    matches.add(new AliasMatch(alias, entry.getValue(), typeMap.get(entry.getValue())));
                }
            });

        return matches;
    }

    /**
     * Build word-boundary pattern for short aliases.
     *
     * <p>Chinese alias (e.g., "腾讯"):
     * rear boundary only — alias must NOT be followed by another CJK char.
     * <ul>
     *   <li>"查腾讯ROI" → 后面是R(non-Han) → match ✓</li>
     *   <li>"腾讯会议" → 后面是会(Han) → no match ✗</li>
     * </ul>
     *
     * <p>English alias (e.g., "TX"): standard \b on both sides.
     */
    private Pattern buildWordBoundaryPattern(String alias) {
        boolean isChinese = alias.chars()
            .anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);

        String escaped = Pattern.quote(alias.toLowerCase());

        if (isChinese) {
            return Pattern.compile(escaped + "(?![\\p{IsHan}])");
        } else {
            return Pattern.compile("\\b" + escaped + "\\b");
        }
    }
}
