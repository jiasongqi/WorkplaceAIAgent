package com.yupi.yuaiagent.perception;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Light heuristic structuring for resume / offer text (no LLM).
 * Cheap first pass before specialist Agents.
 */
@Component
public class ResumeOfferStructurer {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+?86[\\s-]?)?1[3-9]\\d{9}");
    private static final Pattern SALARY = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d+)?\\s*[kKwW万])|(?:月薪|年薪|base|salary)\\s*[:：]?\\s*([\\d,.]+\\s*[kKwW万]?)");
    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*年(?:工作)?经验");

    public Map<String, String> structure(String text, String hint) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (!StringUtils.hasText(text)) {
            return fields;
        }
        String h = hint == null ? "" : hint.toLowerCase(Locale.ROOT);
        boolean offerish = h.contains("offer") || h.contains("薪") || h.contains("negotiation")
                || text.contains("offer") || text.contains("年薪") || text.contains("薪资");

        Matcher email = EMAIL.matcher(text);
        if (email.find()) {
            fields.put("email", email.group());
        }
        Matcher phone = PHONE.matcher(text);
        if (phone.find()) {
            fields.put("phone", phone.group());
        }
        Matcher years = YEARS.matcher(text);
        if (years.find()) {
            fields.put("yearsExperience", years.group(1));
        }
        Matcher salary = SALARY.matcher(text);
        if (salary.find()) {
            String s = salary.group(1) != null ? salary.group(1) : salary.group(2);
            fields.put(offerish ? "offerSalary" : "expectedSalary", s.trim());
        }

        if (text.contains("本科") || text.contains("学士")) {
            fields.put("education", "本科");
        } else if (text.contains("硕士") || text.contains("研究生")) {
            fields.put("education", "硕士");
        } else if (text.contains("博士")) {
            fields.put("education", "博士");
        }

        String docKind = offerish ? "offer" : "resume";
        if (h.contains("resume") || h.contains("简历")) {
            docKind = "resume";
        }
        fields.put("docKind", docKind);
        return fields;
    }
}
