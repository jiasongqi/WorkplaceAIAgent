package com.yupi.yuaiagent.agent.model;

import com.yupi.yuaiagent.agent.model.Appointment.AppointmentStatus;
import com.yupi.yuaiagent.agent.model.Appointment.CalendarProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Appointment 数据模型及其内嵌枚举单元测试
 *
 * <p>覆盖构造（无参 / 全参 / Builder）、默认值，以及
 * {@link AppointmentStatus} 与 {@link CalendarProvider} 枚举的取值与描述。</p>
 *
 * Requirements: 2.7
 */
class AppointmentTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 15, 10, 0);

    @Nested
    @DisplayName("构造与默认值")
    class ConstructionTests {

        @Test
        @DisplayName("无参构造：所有字段默认为 null")
        void noArgsConstructorLeavesFieldsNull() {
            Appointment appointment = new Appointment();
            assertNull(appointment.getAppointmentId());
            assertNull(appointment.getChatId());
            assertNull(appointment.getName());
            assertNull(appointment.getContact());
            assertNull(appointment.getAppointmentTime());
            assertNull(appointment.getTopic());
            assertNull(appointment.getRemark());
            assertNull(appointment.getCalendarEventId());
            assertNull(appointment.getCalendarLink());
            assertNull(appointment.getCalendarProvider());
            assertNull(appointment.getStatus());
            assertNull(appointment.getCreatedAt());
            assertNull(appointment.getUpdatedAt());
        }

        @Test
        @DisplayName("Builder 构造：字段被正确赋值")
        void builderSetsFields() {
            Appointment appointment = Appointment.builder()
                    .appointmentId("apt-1")
                    .chatId("chat-1")
                    .name("小明")
                    .contact("13800138000")
                    .appointmentTime(TIME)
                    .topic("职业规划")
                    .remark("希望下午")
                    .calendarEventId("evt-1")
                    .calendarLink("https://example.com/evt-1")
                    .calendarProvider(CalendarProvider.FEISHU)
                    .status(AppointmentStatus.PENDING)
                    .createdAt(TIME)
                    .updatedAt(TIME)
                    .build();

            assertEquals("apt-1", appointment.getAppointmentId());
            assertEquals("chat-1", appointment.getChatId());
            assertEquals("小明", appointment.getName());
            assertEquals("13800138000", appointment.getContact());
            assertEquals(TIME, appointment.getAppointmentTime());
            assertEquals("职业规划", appointment.getTopic());
            assertEquals("希望下午", appointment.getRemark());
            assertEquals("evt-1", appointment.getCalendarEventId());
            assertEquals("https://example.com/evt-1", appointment.getCalendarLink());
            assertEquals(CalendarProvider.FEISHU, appointment.getCalendarProvider());
            assertEquals(AppointmentStatus.PENDING, appointment.getStatus());
            assertEquals(TIME, appointment.getCreatedAt());
            assertEquals(TIME, appointment.getUpdatedAt());
        }

        @Test
        @DisplayName("全参构造：字段顺序与赋值正确")
        void allArgsConstructor() {
            Appointment appointment = new Appointment(
                    "apt-2", "chat-2", "小红", "test@example.com", TIME,
                    "主题", "备注", "evt-2", "https://example.com/evt-2",
                    CalendarProvider.DINGTALK, AppointmentStatus.CONFIRMED, TIME, TIME);

            assertEquals("apt-2", appointment.getAppointmentId());
            assertEquals("chat-2", appointment.getChatId());
            assertEquals("小红", appointment.getName());
            assertEquals("test@example.com", appointment.getContact());
            assertEquals(CalendarProvider.DINGTALK, appointment.getCalendarProvider());
            assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        }

        @Test
        @DisplayName("equals/hashCode 基于字段值（Lombok @Data）")
        void equalsBasedOnFields() {
            Appointment a = Appointment.builder().appointmentId("x").name("小明").build();
            Appointment b = Appointment.builder().appointmentId("x").name("小明").build();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    @Nested
    @DisplayName("AppointmentStatus 枚举")
    class AppointmentStatusTests {

        @Test
        @DisplayName("包含 PENDING/CONFIRMED/CANCELLED/COMPLETED 四个常量")
        void hasExpectedValues() {
            assertEquals(4, AppointmentStatus.values().length);
            assertNotNull(AppointmentStatus.valueOf("PENDING"));
            assertNotNull(AppointmentStatus.valueOf("CONFIRMED"));
            assertNotNull(AppointmentStatus.valueOf("CANCELLED"));
            assertNotNull(AppointmentStatus.valueOf("COMPLETED"));
        }

        @Test
        @DisplayName("每个常量有对应的中文描述")
        void hasDescriptions() {
            assertEquals("待确认", AppointmentStatus.PENDING.getDescription());
            assertEquals("已确认", AppointmentStatus.CONFIRMED.getDescription());
            assertEquals("已取消", AppointmentStatus.CANCELLED.getDescription());
            assertEquals("已完成", AppointmentStatus.COMPLETED.getDescription());
        }
    }

    @Nested
    @DisplayName("CalendarProvider 枚举")
    class CalendarProviderTests {

        @Test
        @DisplayName("包含 FEISHU/DINGTALK 两个常量")
        void hasExpectedValues() {
            assertEquals(2, CalendarProvider.values().length);
            assertNotNull(CalendarProvider.valueOf("FEISHU"));
            assertNotNull(CalendarProvider.valueOf("DINGTALK"));
        }

        @Test
        @DisplayName("每个常量有对应的中文描述")
        void hasDescriptions() {
            assertEquals("飞书", CalendarProvider.FEISHU.getDescription());
            assertEquals("钉钉", CalendarProvider.DINGTALK.getDescription());
        }
    }
}
