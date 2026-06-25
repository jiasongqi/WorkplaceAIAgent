package com.yupi.yuaiagent.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 信息验证器
 * 验证用户输入的预约信息格式
 * 
 * @author jsq
 */
@Slf4j
@Component
public class InfoValidator {

    // 手机号正则（从文本中搜索）
    private static final Pattern PHONE_SEARCH = Pattern.compile("1[3-9]\\d{9}");
    
    // 邮箱正则（从文本中搜索）
    private static final Pattern EMAIL_SEARCH = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    
    // 姓名正则（中文或英文，2-20个字符）
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z]{2,20}$");

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * 验证姓名
     */
    public ValidationResult validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.failure("姓名不能为空");
        }
        
        String trimmed = name.trim();
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.failure("请输入有效的姓名（2-20个字符，支持中文和英文）");
        }
        
        return ValidationResult.success();
    }

    /**
     * 验证联系方式（手机号或邮箱）
     * 支持从自然语言中提取，如"我的手机号是18104620109"
     */
    public ValidationResult validateContact(String contact) {
        if (contact == null || contact.trim().isEmpty()) {
            return ValidationResult.failure("联系方式不能为空");
        }
        
        String trimmed = contact.trim();
        
        // 检查是否包含手机号
        if (PHONE_SEARCH.matcher(trimmed).find()) {
            return ValidationResult.success();
        }
        
        // 检查是否包含邮箱
        if (EMAIL_SEARCH.matcher(trimmed).find()) {
            return ValidationResult.success();
        }
        
        return ValidationResult.failure("请输入有效的联系方式，手机号格式应为11位数字，邮箱格式为xxx@xxx.com");
    }

    /**
     * 从文本中提取联系方式（手机号或邮箱），返回提取到的值，未找到返回原文
     */
    public String extractContact(String text) {
        if (text == null) return text;
        var phoneMatcher = PHONE_SEARCH.matcher(text);
        if (phoneMatcher.find()) {
            return phoneMatcher.group();
        }
        var emailMatcher = EMAIL_SEARCH.matcher(text);
        if (emailMatcher.find()) {
            return emailMatcher.group();
        }
        return text.trim();
    }

    /**
     * 从自然语言中提取姓名，如"我是小琪"、"小琪是我的姓名"
     */
    public String extractName(String text) {
        if (text == null) return null;
        String trimmed = text.trim();

        // 匹配常见模式：我叫X、我是X、姓名X（搜索整句，不限开头）
        String[] prefixes = {"我叫", "我是", "姓名是", "姓名:", "姓名：", "名字是", "名字:", "名字：", "名字为", "姓名为"};
        for (String prefix : prefixes) {
            int idx = trimmed.indexOf(prefix);
            if (idx >= 0) {
                String after = trimmed.substring(idx + prefix.length()).trim();
                // 提取到下一个空格/标点/常见连接词
                String name = extractNameToken(after);
                if (name != null && name.length() >= 2) return name;
            }
        }

        // "X是我的姓名" / "X是我的名字" 模式
        String[] suffixPatterns = {"是我的姓名", "是我的名字", "是姓名", "是名字"};
        for (String suffix : suffixPatterns) {
            int idx = trimmed.indexOf(suffix);
            if (idx > 0) {
                String before = trimmed.substring(0, idx).trim();
                String name = extractNameToken(before);
                if (name != null && name.length() >= 2) return name;
            }
        }

        // 如果整句就是名字（2-20个中英文字符），直接返回
        if (NAME_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        return trimmed;
    }

    /**
     * 从文本开头提取姓名 token（中文姓名或英文名）
     */
    private String extractNameToken(String text) {
        if (text == null || text.isEmpty()) return null;
        // 取前面连续的中文字符或英文字符作为姓名
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length() && i < 20; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '，' || c == '。' || c == '、' || c == '；') break;
            sb.append(c);
        }
        String token = sb.toString();
        return (token.length() >= 2 && token.length() <= 20) ? token : null;
    }

    /**
     * 验证预约时间
     */
    public ValidationResult validateAppointmentTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return ValidationResult.failure("预约时间不能为空");
        }
        
        LocalDateTime time = extractDateTime(timeStr.trim());
        if (time == null) {
            return ValidationResult.failure("请提供具体的预约时间，如「明天下午3点」或「2024-01-15 14:00」");
        }
        
        // 检查是否为过去时间
        if (time.isBefore(LocalDateTime.now())) {
            return ValidationResult.failure("预约时间不能是过去的时间");
        }
        
        // 检查是否为太远的未来（超过3个月）
        if (time.isAfter(LocalDateTime.now().plusMonths(3))) {
            return ValidationResult.failure("预约时间不能超过3个月");
        }
        
        return ValidationResult.success();
    }

    /**
     * 从自然语言中提取日期时间，如"日期是明天下午三点要是下雨就推辞"
     */
    public LocalDateTime extractDateTime(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String trimmed = text.trim();

        // 处理相对时间
        if (trimmed.contains("明天")) {
            return parseRelativeTime(trimmed, "明天", 1);
        }
        if (trimmed.contains("后天")) {
            return parseRelativeTime(trimmed, "后天", 2);
        }
        if (trimmed.contains("大后天")) {
            return parseRelativeTime(trimmed, "大后天", 3);
        }
        
        // 尝试从文本中找时间模式
        // 匹配 "14:00" 或 "14:00:00"
        var timeMatcher = java.util.regex.Pattern.compile("(\\d{1,2}[:：]\\d{2}(?:[:：]\\d{2})?)").matcher(trimmed);
        if (timeMatcher.find()) {
            String timePart = timeMatcher.group().replace("：", ":");
            // 尝试解析
            for (String pattern : new String[]{"HH:mm:ss", "HH:mm"}) {
                try {
                    var tf = java.time.format.DateTimeFormatter.ofPattern(pattern);
                    var lt = java.time.LocalTime.parse(timePart, tf);
                    return LocalDateTime.of(LocalDate.now(), lt);
                } catch (Exception ignored) {}
            }
        }

        // 尝试标准格式
        String[] patterns = {
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年MM月dd日 HH:mm:ss",
            "MM月dd日 HH:mm",
            "MM月dd日 HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd HH:mm:ss"
        };
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {}
        }
        
        return null;
    }

    /**
     * 验证咨询主题（可选）
     */
    public ValidationResult validateTopic(String topic) {
        // 主题是可选的，可以为空
        if (topic == null || topic.trim().isEmpty()) {
            return ValidationResult.success();
        }
        
        // 检查长度
        if (topic.length() > 200) {
            return ValidationResult.failure("咨询主题不能超过200个字符");
        }
        
        return ValidationResult.success();
    }

    /**
     * 验证备注信息（可选）
     */
    public ValidationResult validateRemark(String remark) {
        // 备注是可选的，可以为空
        if (remark == null || remark.trim().isEmpty()) {
            return ValidationResult.success();
        }
        
        // 检查长度
        if (remark.length() > 500) {
            return ValidationResult.failure("备注信息不能超过500个字符");
        }
        
        return ValidationResult.success();
    }

    /**
     * 验证通用字段（使用正则表达式）
     */
    public ValidationResult validateWithRegex(String value, String regex, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            return ValidationResult.failure("字段不能为空");
        }
        
        if (regex == null || regex.isEmpty()) {
            return ValidationResult.success();
        }
        
        if (!Pattern.matches(regex, value.trim())) {
            return ValidationResult.failure(errorMessage != null ? errorMessage : "格式不正确");
        }
        
        return ValidationResult.success();
    }

    /**
     * 解析日期时间
     */
    public LocalDateTime parseDateTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = timeStr.trim();
        
        // 处理相对时间
        if (trimmed.contains("明天")) {
            return parseRelativeTime(trimmed, "明天", 1);
        }
        if (trimmed.contains("后天")) {
            return parseRelativeTime(trimmed, "后天", 2);
        }
        if (trimmed.contains("大后天")) {
            return parseRelativeTime(trimmed, "大后天", 3);
        }
        
        // 尝试多种格式
        String[] patterns = {
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年MM月dd日 HH:mm:ss",
            "MM月dd日 HH:mm",
            "MM月dd日 HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd HH:mm:ss"
        };
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }
        
        return null;
    }

    /**
     * 解析相对时间（明天、后天等）
     */
    private LocalDateTime parseRelativeTime(String timeStr, String relativeDay, int daysToAdd) {
        try {
            // 移除相对日期关键词
            String timePart = timeStr.replace(relativeDay, "").trim();
            
            // 处理中文时间表达
            timePart = timePart
                    .replace("早上", "AM")
                    .replace("上午", "AM")
                    .replace("中午", "PM")
                    .replace("下午", "PM")
                    .replace("晚上", "PM")
                    .replace("傍晚", "PM");
            
            // 尝试解析时间部分
            LocalDateTime baseTime = LocalDateTime.now().plusDays(daysToAdd);
            
            // 转换中文数字为阿拉伯数字
            String normalized = timePart
                    .replace("零", "0").replace("〇", "0")
                    .replace("一", "1").replace("二", "2").replace("两", "2")
                    .replace("三", "3").replace("四", "4").replace("五", "5")
                    .replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9");
            
            // 尝试解析 "PM3点" 或 "PM3点30分" 这种格式
            if (normalized.matches(".*[AP]M\\d+点.*")) {
                boolean isPM = normalized.contains("PM");
                String hourStr = normalized.replaceAll(".*[AP]M(\\d+)点.*", "$1");
                int hour = Integer.parseInt(hourStr);
                if (isPM && hour < 12) {
                    hour += 12;
                }
                // 尝试提取分钟
                int minute = 0;
                if (normalized.contains("分")) {
                    String minStr = normalized.replaceAll(".*点(\\d+)分.*", "$1");
                    if (minStr.matches("\\d+")) {
                        minute = Integer.parseInt(minStr);
                    }
                }
                return baseTime.withHour(hour).withMinute(minute).withSecond(0);
            }

            // 尝试解析纯 "10点" 或 "10点30分" 格式（无 AM/PM 前缀）
            if (normalized.matches(".*\\d+点.*")) {
                String hourStr = normalized.replaceAll(".*(\\d+)点.*", "$1");
                int hour = Integer.parseInt(hourStr);
                int minute = 0;
                if (normalized.contains("半")) {
                    minute = 30;
                } else if (normalized.contains("分")) {
                    String minStr = normalized.replaceAll(".*点(\\d+)分.*", "$1");
                    if (minStr.matches("\\d+")) {
                        minute = Integer.parseInt(minStr);
                    }
                }
                return baseTime.withHour(hour).withMinute(minute).withSecond(0);
            }
            
            // 尝试解析 "14:00" 这种格式
            if (normalized.matches(".*\\d{1,2}:\\d{2}.*")) {
                var matcher = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})").matcher(normalized);
                if (matcher.find()) {
                    String[] parts = matcher.group().split(":");
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    return baseTime.withHour(hour).withMinute(minute).withSecond(0);
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("解析相对时间失败：{}", timeStr, e);
            return null;
        }
    }

    /**
     * 格式化日期时间
     */
    public String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
