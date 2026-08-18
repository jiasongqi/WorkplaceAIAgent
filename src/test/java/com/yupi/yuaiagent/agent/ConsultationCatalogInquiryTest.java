package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.Appointment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultationCatalogInquiryTest {

    @Test
    void detectsCatalogQuestions() {
        assertThat(ConsultationAgent.isServiceCatalogInquiry("先告诉我你们有什么可以预约")).isTrue();
        assertThat(ConsultationAgent.isServiceCatalogInquiry("有什么可以预约")).isTrue();
        assertThat(ConsultationAgent.isServiceCatalogInquiry("能预约什么服务")).isTrue();
        assertThat(ConsultationAgent.isServiceCatalogInquiry("我想预约明天三点简历咨询")).isFalse();
        assertThat(ConsultationAgent.isServiceCatalogInquiry("明天下午3点")).isFalse();
    }

    @Test
    void detectsCancel() {
        assertThat(ConsultationAgent.isCancelBooking("取消预约")).isTrue();
        assertThat(ConsultationAgent.isCancelBooking("不约了")).isTrue();
        assertThat(ConsultationAgent.isCancelBooking("明天下午3点")).isFalse();
    }

    @Test
    void keywordRouterKeepsExplicitBooking() {
        assertThat(KeywordRouter.keywordRouteIntent("我想预约一位职业顾问咨询"))
                .isEqualTo(AgentIntent.CONSULTATION);
        assertThat(KeywordRouter.keywordRouteIntent("有什么可以预约"))
                .isEqualTo(AgentIntent.CONSULTATION);
        assertThat(KeywordRouter.keywordRouteIntent("我不确定自己的职业方向 有什么可以预约的课程"))
                .isEqualTo(AgentIntent.CONSULTATION);
        assertThat(KeywordRouter.hasMultiDomainConflict("我不确定自己的职业方向 有什么可以预约的课程"))
                .isFalse();
        // 单独「咨询一下」不再硬路由到预约填表
        assertThat(KeywordRouter.keywordRouteIntent("想咨询一下怎么涨薪"))
                .isEqualTo(AgentIntent.NEGOTIATION);
    }

    @Test
    void keywordRouterRoutesScheduleInquiryToConsultation() {
        assertThat(KeywordRouter.keywordRouteIntent("看下我的日程安排"))
                .isEqualTo(AgentIntent.CONSULTATION);
        assertThat(KeywordRouter.keywordRouteIntent("查看我的预约"))
                .isEqualTo(AgentIntent.CONSULTATION);
        assertThat(KeywordRouter.keywordRouteIntent("我的预约进度怎么样"))
                .isEqualTo(AgentIntent.CONSULTATION);
    }

    @Test
    void detectsScheduleInquiry() {
        assertThat(ConsultationAgent.isScheduleInquiry("看下我的日程安排")).isTrue();
        assertThat(ConsultationAgent.isScheduleInquiry("查看我的预约")).isTrue();
        assertThat(ConsultationAgent.isScheduleInquiry("今天有我的预约吗")).isTrue();
        assertThat(ConsultationAgent.isScheduleInquiry("今天有我的已预约课程吗")).isTrue();
        assertThat(ConsultationAgent.isScheduleInquiry("我想预约明天三点简历咨询")).isFalse();
        assertThat(ConsultationAgent.isScheduleInquiry("有什么可以预约")).isFalse();
    }

    @Test
    void scheduleInquiryIsNotCatalogInquiry() {
        assertThat(ConsultationAgent.isServiceCatalogInquiry("今天有我的已预约课程吗")).isFalse();
        assertThat(ConsultationAgent.isServiceCatalogInquiry("今天有我的预约吗")).isFalse();
        assertThat(ConsultationAgent.isServiceCatalogInquiry("我不确定自己的职业方向 有什么可以预约的课程"))
                .isTrue();
    }

    @Test
    void detectsTodayScheduleInquiry() {
        assertThat(ConsultationAgent.isTodayScheduleInquiry("今天有我的预约吗")).isTrue();
        assertThat(ConsultationAgent.isTodayScheduleInquiry("今天有我的已预约课程吗")).isTrue();
        assertThat(ConsultationAgent.isTodayScheduleInquiry("今天还有预约吗")).isTrue();
        assertThat(ConsultationAgent.isTodayScheduleInquiry("看下我的日程安排")).isFalse();
    }

    @Test
    void todayFilterExcludesPastAppointments() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        Appointment past = Appointment.builder()
                .appointmentId("past-1")
                .topic("职业方向梳理")
                .appointmentTime(LocalDateTime.of(2026, 7, 29, 15, 0))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build();
        Appointment todayAppt = Appointment.builder()
                .appointmentId("today-1")
                .topic("谈薪咨询")
                .appointmentTime(LocalDateTime.of(2026, 8, 14, 10, 0))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build();

        List<Appointment> filtered = ConsultationAgent.filterAppointmentsForInquiry(
                List.of(past, todayAppt),
                true,
                today
        );

        assertThat(filtered).extracting(Appointment::getAppointmentId).containsExactly("today-1");
    }

    @Test
    void allScheduleInquiryKeepsPastButMarksThemExpired() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 11, 0);
        Appointment past = Appointment.builder()
                .appointmentId("past-1")
                .appointmentTime(LocalDateTime.of(2026, 7, 29, 15, 0))
                .status(Appointment.AppointmentStatus.CONFIRMED)
                .build();

        assertThat(ConsultationAgent.resolveDisplayStatus(past, now)).isEqualTo("已过期");
    }
}
