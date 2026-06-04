package com.yupi.yuaiagent.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    // 手机号正则
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    
    // 邮箱正则
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    
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
     */
    public ValidationResult validateContact(String contact) {
        if (contact == null || contact.trim().isEmpty()) {
            return ValidationResult.failure("联系方式不能为空");
        }
        
        String trimmed = contact.trim();
        
        // 检查是否为手机号
        if (PHONE_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.success();
        }
        
        // 检查是否为邮箱
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.success();
        }
        
        return ValidationResult.failure("请输入有效的联系方式，手机号格式应为11位数字，邮箱格式为xxx@xxx.com");
    }

    /**
     * 验证预约时间
     */
    public ValidationResult validateAppointmentTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return ValidationResult.failure("预约时间不能为空");
        }
        
        String trimmed = timeStr.trim();
        
        // 尝试解析时间
        LocalDateTime time = parseDateTime(trimmed);
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
                    .replace("上午", "AM")
                    .replace("下午", "PM")
                    .replace("晚上", "PM");
            
            // 尝试解析时间部分
            LocalDateTime baseTime = LocalDateTime.now().plusDays(daysToAdd);
            
            // 尝试解析 "下午3点" 这种格式
            if (timePart.matches(".*[AP]M\\d+点.*")) {
                boolean isPM = timePart.contains("PM");
                String hourStr = timePart.replaceAll(".*[AP]M(\\d+)点.*", "$1");
                int hour = Integer.parseInt(hourStr);
                if (isPM && hour < 12) {
                    hour += 12;
                }
                return baseTime.withHour(hour).withMinute(0).withSecond(0);
            }
            
            // 尝试解析 "14:00" 这种格式
            if (timePart.matches("\\d{1,2}:\\d{2}")) {
                String[] parts = timePart.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                return baseTime.withHour(hour).withMinute(minute).withSecond(0);
            }
            
            // 尝试解析 "14:00:00" 这种格式
            if (timePart.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                String[] parts = timePart.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                int second = Integer.parseInt(parts[2]);
                return baseTime.withHour(hour).withMinute(minute).withSecond(second);
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
