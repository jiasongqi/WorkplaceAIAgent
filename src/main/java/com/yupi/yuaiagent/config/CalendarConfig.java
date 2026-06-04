package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.agent.model.Appointment;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日历服务配置（绑定 application.yml 中的 {@code calendar.*}）。
 *
 * <p>集中承载日历对接所需的提供商选择与飞书/钉钉应用凭证，对应 Requirements 2.1
 * （统一日历服务配置）/ 2.5（FEISHU）/ 2.6（DINGTALK）。该配置由
 * {@link AgentConfig} 通过 {@code @EnableConfigurationProperties} 装配为 Bean，
 * 与 {@link com.yupi.yuaiagent.calendar.CalendarServiceFactory} 的
 * {@code calendar.provider} 选择保持一致。
 *
 * <p>所有密钥均通过 application.yml 的环境变量占位符注入（如 {@code ${FEISHU_APP_ID:}}），
 * 不在代码或配置中硬编码真实凭证。
 *
 * @author jsq
 */
@Data
@ConfigurationProperties(prefix = "calendar")
public class CalendarConfig {

    /**
     * 日历服务提供商：FEISHU 或 DINGTALK，默认 FEISHU。
     * <p>至少需配置一个提供商，以保证 CalendarServiceFactory 注入的
     * {@code List<CalendarService>} 非空。
     */
    private Appointment.CalendarProvider provider = Appointment.CalendarProvider.FEISHU;

    /**
     * 飞书开放平台配置
     */
    private FeishuConfig feishu = new FeishuConfig();

    /**
     * 钉钉开放平台配置
     */
    private DingTalkConfig dingtalk = new DingTalkConfig();

    /**
     * 飞书日历配置
     */
    @Data
    public static class FeishuConfig {
        /** 飞书应用 App ID（环境变量 FEISHU_APP_ID 注入） */
        private String appId;
        /** 飞书应用 App Secret（环境变量 FEISHU_APP_SECRET 注入） */
        private String appSecret;
        /** 飞书开放平台 API 基地址 */
        private String baseUrl = "https://open.feishu.cn/open-apis";
    }

    /**
     * 钉钉日历配置
     */
    @Data
    public static class DingTalkConfig {
        /** 钉钉应用 App Key（环境变量 DINGTALK_APP_KEY 注入） */
        private String appKey;
        /** 钉钉应用 App Secret（环境变量 DINGTALK_APP_SECRET 注入） */
        private String appSecret;
        /** 钉钉开放平台 API 基地址 */
        private String baseUrl = "https://api.dingtalk.com";
    }
}
