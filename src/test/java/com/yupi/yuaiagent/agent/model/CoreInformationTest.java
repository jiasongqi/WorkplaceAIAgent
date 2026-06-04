package com.yupi.yuaiagent.agent.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CoreInformation 数据模型单元测试
 *
 * <p>覆盖 {@code isComplete()} 与 {@code getMissingFields()} 的边界条件，
 * 以及 {@code toAppointment(...)} 转换方法。</p>
 *
 * Requirements: 5.2
 */
class CoreInformationTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 15, 10, 0);

    private static CoreInformation complete() {
        return CoreInformation.builder()
                .name("小明")
                .contact("13800138000")
                .appointmentTime(TIME)
                .build();
    }

    @Nested
    @DisplayName("isComplete() 边界条件")
    class IsCompleteTests {

        @Test
        @DisplayName("三项核心信息齐全时返回 true")
        void allCoreFieldsPresent() {
            assertTrue(complete().isComplete());
        }

        @Test
        @DisplayName("无参构造（全部为 null）时返回 false")
        void emptyInformation() {
            assertFalse(new CoreInformation().isComplete());
        }

        @Test
        @DisplayName("name 为 null 时返回 false")
        void nameNull() {
            CoreInformation info = complete();
            info.setName(null);
            assertFalse(info.isComplete());
        }

        @Test
        @DisplayName("name 为空字符串时返回 false")
        void nameEmpty() {
            CoreInformation info = complete();
            info.setName("");
            assertFalse(info.isComplete());
        }

        @Test
        @DisplayName("contact 为 null 时返回 false")
        void contactNull() {
            CoreInformation info = complete();
            info.setContact(null);
            assertFalse(info.isComplete());
        }

        @Test
        @DisplayName("contact 为空字符串时返回 false")
        void contactEmpty() {
            CoreInformation info = complete();
            info.setContact("");
            assertFalse(info.isComplete());
        }

        @Test
        @DisplayName("appointmentTime 为 null 时返回 false")
        void appointmentTimeNull() {
            CoreInformation info = complete();
            info.setAppointmentTime(null);
            assertFalse(info.isComplete());
        }

        @Test
        @DisplayName("非核心字段（topic/remark）缺失不影响完整性判定")
        void optionalFieldsDoNotAffectCompleteness() {
            CoreInformation info = complete();
            info.setTopic(null);
            info.setRemark(null);
            assertTrue(info.isComplete());
        }
    }

    @Nested
    @DisplayName("getMissingFields() 边界条件")
    class GetMissingFieldsTests {

        @Test
        @DisplayName("信息齐全时返回空列表")
        void noMissingWhenComplete() {
            assertTrue(complete().getMissingFields().isEmpty());
        }

        @Test
        @DisplayName("无参构造时返回全部三项核心字段，且顺序为 name/contact/appointmentTime")
        void allMissingWhenEmpty() {
            List<String> missing = new CoreInformation().getMissingFields();
            assertIterableEquals(List.of("name", "contact", "appointmentTime"), missing);
        }

        @Test
        @DisplayName("仅缺少 name 时只返回 name")
        void onlyNameMissing() {
            CoreInformation info = complete();
            info.setName(null);
            assertIterableEquals(List.of("name"), info.getMissingFields());
        }

        @Test
        @DisplayName("仅缺少 contact 时只返回 contact")
        void onlyContactMissing() {
            CoreInformation info = complete();
            info.setContact("");
            assertIterableEquals(List.of("contact"), info.getMissingFields());
        }

        @Test
        @DisplayName("仅缺少 appointmentTime 时只返回 appointmentTime")
        void onlyTimeMissing() {
            CoreInformation info = complete();
            info.setAppointmentTime(null);
            assertIterableEquals(List.of("appointmentTime"), info.getMissingFields());
        }

        @Test
        @DisplayName("空字符串与 null 都被视为缺失")
        void emptyStringTreatedAsMissing() {
            CoreInformation info = CoreInformation.builder()
                    .name("")
                    .contact(null)
                    .appointmentTime(null)
                    .build();
            assertIterableEquals(List.of("name", "contact", "appointmentTime"), info.getMissingFields());
        }

        @Test
        @DisplayName("getMissingFields() 为空 当且仅当 isComplete() 为 true")
        void consistentWithIsComplete() {
            CoreInformation completeInfo = complete();
            assertTrue(completeInfo.isComplete());
            assertTrue(completeInfo.getMissingFields().isEmpty());

            CoreInformation incomplete = complete();
            incomplete.setContact(null);
            assertFalse(incomplete.isComplete());
            assertFalse(incomplete.getMissingFields().isEmpty());
        }
    }

    @Nested
    @DisplayName("toAppointment(...) 转换")
    class ToAppointmentTests {

        @Test
        @DisplayName("核心字段被正确映射，状态默认 PENDING，提供商被设置")
        void mapsFieldsAndDefaults() {
            CoreInformation info = CoreInformation.builder()
                    .name("小红")
                    .contact("test@example.com")
                    .appointmentTime(TIME)
                    .topic("职业规划")
                    .remark("希望下午")
                    .build();

            Appointment appointment = info.toAppointment("chat-1", Appointment.CalendarProvider.FEISHU);

            assertNotNull(appointment);
            assertEquals("chat-1", appointment.getChatId());
            assertEquals("小红", appointment.getName());
            assertEquals("test@example.com", appointment.getContact());
            assertEquals(TIME, appointment.getAppointmentTime());
            assertEquals("职业规划", appointment.getTopic());
            assertEquals("希望下午", appointment.getRemark());
            assertEquals(Appointment.CalendarProvider.FEISHU, appointment.getCalendarProvider());
            assertEquals(Appointment.AppointmentStatus.PENDING, appointment.getStatus());
            assertNotNull(appointment.getCreatedAt());
            assertNotNull(appointment.getUpdatedAt());
        }

        @Test
        @DisplayName("可使用 DINGTALK 提供商进行转换")
        void supportsDingTalkProvider() {
            Appointment appointment = complete().toAppointment("chat-2", Appointment.CalendarProvider.DINGTALK);
            assertEquals(Appointment.CalendarProvider.DINGTALK, appointment.getCalendarProvider());
        }
    }
}
