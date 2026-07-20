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
        // 单独「咨询一下」不再硬路由到预约填表
        assertThat(KeywordRouter.keywordRouteIntent("想咨询一下怎么涨薪"))
                .isEqualTo(AgentIntent.NEGOTIATION);
    }
}
