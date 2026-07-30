package com.yupi.yuaiagent.validation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * InfoValidator 信息校验单元测试
 *
 * <p>覆盖手机号、邮箱、姓名的有效/无效用例，以及预约时间解析（绝对格式 + 相对表达）
 * 与过去时间 / 超过 3 个月的边界拒绝。为避免与 {@code LocalDateTime.now()} 相关的
 * 时间漂移导致测试不稳定，所有"有效预约时间"断言均使用相对/未来时间动态构造。
 *
 * Requirements: 5.6
 */
@DisplayName("InfoValidator 信息校验单元测试")
class InfoValidatorTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InfoValidator validator = new InfoValidator();

    // =====================================================================
    // 联系方式 - 手机号 (^1[3-9]\d{9}$)
    // =====================================================================
    @Nested
    @DisplayName("validateContact - 手机号")
    class PhoneValidation {

        @ParameterizedTest(name = "有效手机号: {0}")
        @ValueSource(strings = {
                "13800138000",  // 1 + 3
                "15012345678",  // 1 + 5
                "18612345678",  // 1 + 8
                "19987654321",  // 1 + 9
                "17712345678"   // 1 + 7
        })
        @DisplayName("符合 ^1[3-9]\\d{9}$ 的手机号校验通过")
        void validPhones(String phone) {
            InfoValidator.ValidationResult result = validator.validateContact(phone);
            Assertions.assertTrue(result.isValid(), "应判定为有效: " + phone);
        }

        @ParameterizedTest(name = "无效手机号: {0}")
        @ValueSource(strings = {
                "12800138000",  // 第二位为 2，不在 3-9 范围
                "10800138000",  // 第二位为 0
                "23800138000",  // 不以 1 开头
                "1380013800",   // 仅 10 位
                "138001380000", // 12 位
                "1380013800a",  // 含字母
                "138 0013 8000" // 含空格
        })
        @DisplayName("不符合手机号规则的输入校验失败并返回格式提示")
        void invalidPhones(String phone) {
            InfoValidator.ValidationResult result = validator.validateContact(phone);
            Assertions.assertFalse(result.isValid(), "应判定为无效: " + phone);
            Assertions.assertNotNull(result.getMessage());
        }
    }

    // =====================================================================
    // 联系方式 - 邮箱
    // =====================================================================
    @Nested
    @DisplayName("validateContact - 邮箱")
    class EmailValidation {

        @ParameterizedTest(name = "有效邮箱: {0}")
        @ValueSource(strings = {
                "example@email.com",
                "user.name@domain.cn",
                "user+tag@sub.domain.com",
                "first_last@company.org",
                "abc123@test-domain.io"
        })
        @DisplayName("合法邮箱校验通过")
        void validEmails(String email) {
            Assertions.assertTrue(validator.validateContact(email).isValid(), "应判定为有效: " + email);
        }

        @ParameterizedTest(name = "无效邮箱: {0}")
        @ValueSource(strings = {
                "plainaddress",        // 无 @
                "user@domain",         // 无顶级域
                "@domain.com",         // 无本地部分
                "user@.com",           // 域名缺失
                "user@domain.c",       // 顶级域不足 2 位
                "user@@domain.com"     // 双 @
        })
        @DisplayName("非法邮箱校验失败并返回格式提示")
        void invalidEmails(String email) {
            InfoValidator.ValidationResult result = validator.validateContact(email);
            Assertions.assertFalse(result.isValid(), "应判定为无效: " + email);
            Assertions.assertNotNull(result.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("空联系方式校验失败")
        void blankContact(String contact) {
            Assertions.assertFalse(validator.validateContact(contact).isValid());
        }
    }

    // =====================================================================
    // 姓名
    // =====================================================================
    @Nested
    @DisplayName("validateName")
    class NameValidation {

        @ParameterizedTest(name = "有效姓名: {0}")
        @ValueSource(strings = {"张三", "李四", "John", "Alice", "欧阳娜娜"})
        @DisplayName("中英文 2-20 字符的姓名校验通过")
        void validNames(String name) {
            Assertions.assertTrue(validator.validateName(name).isValid(), "应判定为有效: " + name);
        }

        @ParameterizedTest(name = "无效姓名: {0}")
        @ValueSource(strings = {
                "李",          // 仅 1 个字符
                "A",           // 仅 1 个字符
                "张三123",      // 含数字
                "John Doe",    // 含空格
                "user@name"    // 含特殊字符
        })
        @DisplayName("不符合规则的姓名校验失败")
        void invalidNames(String name) {
            Assertions.assertFalse(validator.validateName(name).isValid(), "应判定为无效: " + name);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("空姓名校验失败")
        void blankName(String name) {
            Assertions.assertFalse(validator.validateName(name).isValid());
        }
    }

    // =====================================================================
    // 时间解析 parseDateTime
    // =====================================================================
    @Nested
    @DisplayName("parseDateTime - 时间解析")
    class ParseDateTime {

        @Test
        @DisplayName("解析 yyyy-MM-dd HH:mm 绝对格式")
        void parseDashFormat() {
            LocalDateTime time = validator.parseDateTime("2024-01-15 14:00");
            Assertions.assertEquals(LocalDateTime.of(2024, 1, 15, 14, 0), time);
        }

        @Test
        @DisplayName("解析 yyyy年MM月dd日 HH:mm 中文格式")
        void parseChineseFormat() {
            LocalDateTime time = validator.parseDateTime("2024年01月15日 10:30");
            Assertions.assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), time);
        }

        @Test
        @DisplayName("解析 yyyy/MM/dd HH:mm 斜杠格式")
        void parseSlashFormat() {
            LocalDateTime time = validator.parseDateTime("2024/03/20 09:15");
            Assertions.assertEquals(LocalDateTime.of(2024, 3, 20, 9, 15), time);
        }

        @Test
        @DisplayName("解析「明天下午3点」相对表达 -> 次日 15:00")
        void parseRelativeTomorrowAfternoon() {
            LocalDateTime time = validator.parseDateTime("明天下午3点");
            Assertions.assertNotNull(time);
            Assertions.assertEquals(LocalDate.now().plusDays(1), time.toLocalDate());
            Assertions.assertEquals(15, time.getHour());
            Assertions.assertEquals(0, time.getMinute());
        }

        @ParameterizedTest(name = "带空格相对时间: {0}")
        @ValueSource(strings = {
                "明天下午 3 点",
                "明天下午3 点",
                "预约职业方向梳理，明天下午 3 点"
        })
        @DisplayName("「明天下午 3 点」等带空格表达也能解析为次日 15:00")
        void parseRelativeTomorrowAfternoonWithSpaces(String input) {
            LocalDateTime time = validator.extractDateTime(input);
            Assertions.assertNotNull(time, "应能解析: " + input);
            Assertions.assertEquals(LocalDate.now().plusDays(1), time.toLocalDate());
            Assertions.assertEquals(15, time.getHour());
            Assertions.assertEquals(0, time.getMinute());
        }

        @Test
        @DisplayName("「明天下午 3 点 30 分」带空格含分钟 -> 次日 15:30")
        void parseRelativeTomorrowAfternoonWithSpacesAndMinutes() {
            LocalDateTime time = validator.extractDateTime("明天下午 3 点 30 分");
            Assertions.assertNotNull(time);
            Assertions.assertEquals(LocalDate.now().plusDays(1), time.toLocalDate());
            Assertions.assertEquals(15, time.getHour());
            Assertions.assertEquals(30, time.getMinute());
        }

        @Test
        @DisplayName("解析「后天 10:00」相对表达 -> 第三日 10:00")
        void parseRelativeDayAfterTomorrow() {
            LocalDateTime time = validator.parseDateTime("后天 10:00");
            Assertions.assertNotNull(time);
            Assertions.assertEquals(LocalDate.now().plusDays(2), time.toLocalDate());
            Assertions.assertEquals(10, time.getHour());
            Assertions.assertEquals(0, time.getMinute());
        }

        @ParameterizedTest(name = "无法解析: {0}")
        @ValueSource(strings = {"随便什么时候", "not a time", "明年某天", "13580"})
        @DisplayName("无法识别的时间字符串返回 null")
        void unparseableReturnsNull(String input) {
            Assertions.assertNull(validator.parseDateTime(input));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("空字符串解析返回 null")
        void blankReturnsNull(String input) {
            Assertions.assertNull(validator.parseDateTime(input));
        }
    }

    // =====================================================================
    // 预约时间校验 validateAppointmentTime
    // =====================================================================
    @Nested
    @DisplayName("validateAppointmentTime - 预约时间校验")
    class ValidateAppointmentTime {

        @Test
        @DisplayName("未来 10 天的有效绝对时间校验通过")
        void futureAbsoluteTimeIsValid() {
            String future = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).format(FMT);
            InfoValidator.ValidationResult result = validator.validateAppointmentTime(future);
            Assertions.assertTrue(result.isValid(), "应判定为有效: " + future);
        }

        @Test
        @DisplayName("「明天下午3点」相对未来时间校验通过")
        void relativeFutureTimeIsValid() {
            Assertions.assertTrue(validator.validateAppointmentTime("明天下午3点").isValid());
        }

        @Test
        @DisplayName("过去时间被拒绝")
        void pastTimeRejected() {
            InfoValidator.ValidationResult result = validator.validateAppointmentTime("2020-01-01 10:00");
            Assertions.assertFalse(result.isValid());
            Assertions.assertTrue(result.getMessage().contains("过去"));
        }

        @Test
        @DisplayName("超过 3 个月的时间被拒绝")
        void overThreeMonthsRejected() {
            String tooFar = LocalDateTime.now().plusMonths(4).withHour(10).withMinute(0).format(FMT);
            InfoValidator.ValidationResult result = validator.validateAppointmentTime(tooFar);
            Assertions.assertFalse(result.isValid());
            Assertions.assertTrue(result.getMessage().contains("3个月"));
        }

        @Test
        @DisplayName("无法解析的时间返回格式提示")
        void unparseableReturnsHint() {
            InfoValidator.ValidationResult result = validator.validateAppointmentTime("某个时间");
            Assertions.assertFalse(result.isValid());
            Assertions.assertNotNull(result.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("空预约时间校验失败")
        void blankRejected(String input) {
            Assertions.assertFalse(validator.validateAppointmentTime(input).isValid());
        }
    }

    // =====================================================================
    // 格式化 formatDateTime
    // =====================================================================
    @Nested
    @DisplayName("formatDateTime - 时间格式化")
    class FormatDateTime {

        @Test
        @DisplayName("格式化为 yyyy-MM-dd HH:mm")
        void formatsToPattern() {
            String text = validator.formatDateTime(LocalDateTime.of(2024, 1, 15, 14, 0));
            Assertions.assertEquals("2024-01-15 14:00", text);
        }

        @Test
        @DisplayName("null 输入返回 null")
        void nullReturnsNull() {
            Assertions.assertNull(validator.formatDateTime(null));
        }

        @Test
        @DisplayName("parseDateTime 与 formatDateTime 对绝对格式可往返")
        void parseFormatRoundTrip() {
            String original = "2024-12-25 18:30";
            LocalDateTime parsed = validator.parseDateTime(original);
            Assertions.assertEquals(original, validator.formatDateTime(parsed));
        }
    }
}
