package com.yupi.yuaiagent.agent;

import org.junit.jupiter.api.Test;

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
        assertThat(ConsultationAgent.isScheduleInquiry("我想预约明天三点简历咨询")).isFalse();
        assertThat(ConsultationAgent.isScheduleInquiry("有什么可以预约")).isFalse();
    }
}
