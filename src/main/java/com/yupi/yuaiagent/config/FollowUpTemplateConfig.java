package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.agent.model.CoreInfoType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追问模板配置
 * <p>
 * 提供姓名、联系方式、预约时间三类核心信息的追问模板配置，以及确认页、
 * 校验失败、预约成功、预约失败等场景的渲染模板。
 * <p>
 * 未配置时回退到内置默认模板（见 {@link #afterPropertiesSet()}）。
 * <p>
 * 热更新（无需重启服务即生效）：
 * 由于本项目未引入 Spring Cloud（无 {@code @RefreshScope}），采用设计文档所述的
 * 「事件监听 + 运行时更新」机制实现热更新——
 * <ul>
 *   <li>核心/非核心模板使用线程安全的 {@link ConcurrentHashMap} 存储，
 *       运行时通过 {@code updateXxx} 方法修改即时生效；</li>
 *   <li>通过发布 {@link TemplateRefreshEvent} 事件触发 {@link #refreshTemplates(TemplateRefreshEvent)}，
 *       重新载入模板，无需重启即可生效。</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "consultation.follow-up")
public class FollowUpTemplateConfig implements InitializingBean {

    /**
     * 核心信息追问模板（线程安全，支持运行时热更新）
     */
    private Map<String, TemplateConfig> core = new ConcurrentHashMap<>();

    /**
     * 非核心信息追问模板（线程安全，支持运行时热更新）
     */
    private Map<String, TemplateConfig> optional = new ConcurrentHashMap<>();

    /**
     * 确认信息模板
     */
    private String confirmation = "请确认以下预约信息：\n\n姓名：{name}\n联系方式：{contact}\n预约时间：{appointmentTime}\n{topicLine}\n{remarkLine}\n\n请回复\"确认\"创建预约，或回复\"修改\"重新填写信息。";

    /**
     * 预约成功模板
     */
    private String success = "预约创建成功！\n\n预约编号：{appointmentId}\n预约人：{name}\n预约时间：{appointmentTime}\n\n我们会通过 {contact} 与您确认预约详情。\n如需修改或取消预约，请随时联系我。";

    /**
     * 预约失败模板
     */
    private String failure = "抱歉，创建预约时出现错误。\n\n错误信息：{errorMessage}\n\n请稍后重试或联系人工客服。";

    /**
     * 验证失败模板
     */
    private String validationFailed = "输入信息格式不正确：\n\n{validationMessage}\n\n请重新输入。";

    /**
     * 时间格式说明模板
     */
    private String timeFormatHint = "请提供预约时间，支持以下格式：\n- 2024-01-15 14:00\n- 2024年1月15日 14:00\n- 明天下午3点\n- 后天上午10点";

    /**
     * 模板配置内部类
     */
    @Data
    public static class TemplateConfig {
        /**
         * 字段名
         */
        private String fieldName;

        /**
         * 显示名称
         */
        private String displayName;

        /**
         * 问题内容
         */
        private String question;

        /**
         * 验证正则表达式
         */
        private String validationRegex;

        /**
         * 验证失败提示
         */
        private String validationMessage;

        /**
         * 优先级
         */
        private int priority;
    }

    /**
     * 模板热更新事件
     * <p>
     * 管理端修改模板后发布此事件，{@link #refreshTemplates(TemplateRefreshEvent)} 监听并重载，
     * 实现无需重启的热更新。
     */
    public static class TemplateRefreshEvent {
    }

    /**
     * 获取核心信息字段配置，不存在时回退到默认模板
     */
    public TemplateConfig getCoreTemplate(String fieldName) {
        TemplateConfig config = core.get(fieldName);
        if (config == null) {
            config = buildDefaultCoreTemplate(fieldName);
            if (config != null) {
                log.debug("核心模板[{}]未配置，回退到默认模板", fieldName);
            }
        }
        return config;
    }

    /**
     * 获取核心信息字段配置（基于 {@link CoreInfoType}），不存在时回退到默认模板
     */
    public TemplateConfig getCoreTemplate(CoreInfoType type) {
        if (type == null) {
            return null;
        }
        return getCoreTemplate(type.getFieldName());
    }

    /**
     * 获取非核心信息字段配置
     */
    public TemplateConfig getOptionalTemplate(String fieldName) {
        return optional.get(fieldName);
    }

    /**
     * 运行时更新核心模板（热更新，立即生效）
     */
    public void updateCoreTemplate(String fieldName, TemplateConfig config) {
        if (fieldName == null || config == null) {
            return;
        }
        core.put(fieldName, config);
        log.info("核心追问模板[{}]已热更新", fieldName);
    }

    /**
     * 运行时更新非核心模板（热更新，立即生效）
     */
    public void updateOptionalTemplate(String fieldName, TemplateConfig config) {
        if (fieldName == null || config == null) {
            return;
        }
        optional.put(fieldName, config);
        log.info("非核心追问模板[{}]已热更新", fieldName);
    }

    /**
     * 运行时更新确认页模板（热更新，立即生效）
     */
    public void updateConfirmationTemplate(String template) {
        if (template != null) {
            this.confirmation = template;
            log.info("确认页模板已热更新");
        }
    }

    /**
     * 渲染确认信息模板
     */
    public String renderConfirmation(String name, String contact, String appointmentTime,
                                     String topic, String remark) {
        String topicLine = (topic != null && !topic.isEmpty()) ? "咨询主题：" + topic : "";
        String remarkLine = (remark != null && !remark.isEmpty()) ? "备注：" + remark : "";

        Map<String, String> context = new LinkedHashMap<>();
        context.put("name", name != null ? name : "未提供");
        context.put("contact", contact != null ? contact : "未提供");
        context.put("appointmentTime", appointmentTime != null ? appointmentTime : "未指定");
        context.put("topicLine", topicLine);
        context.put("remarkLine", remarkLine);

        return applyPlaceholders(confirmation, context);
    }

    /**
     * 渲染预约成功模板
     */
    public String renderSuccess(String appointmentId, String name, String appointmentTime, String contact) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("appointmentId", appointmentId != null ? appointmentId : "未知");
        context.put("name", name != null ? name : "未提供");
        context.put("appointmentTime", appointmentTime != null ? appointmentTime : "未指定");
        context.put("contact", contact != null ? contact : "未提供");
        return applyPlaceholders(success, context);
    }

    /**
     * 渲染预约失败模板
     */
    public String renderFailure(String errorMessage) {
        return applyPlaceholders(failure,
                Map.of("errorMessage", errorMessage != null ? errorMessage : "未知错误"));
    }

    /**
     * 渲染验证失败模板
     */
    public String renderValidationFailed(String validationMessage) {
        return applyPlaceholders(validationFailed,
                Map.of("validationMessage", validationMessage != null ? validationMessage : "格式不正确"));
    }

    /**
     * 渲染核心信息追问问题（支持占位符替换）
     * <p>
     * 若指定字段已配置模板则使用配置模板，否则回退到默认模板（Property 11）。
     * 问题文本中的 {@code {key}} 占位符将替换为 {@code context} 中的对应值（Property 14）。
     *
     * @param type    核心信息类型
     * @param context 占位符上下文（可为 null）
     * @return 渲染后的追问问题文本；模板不存在时返回 null
     */
    public String renderCoreQuestion(CoreInfoType type, Map<String, String> context) {
        TemplateConfig config = getCoreTemplate(type);
        if (config == null || config.getQuestion() == null) {
            return null;
        }
        return applyPlaceholders(config.getQuestion(), context);
    }

    /**
     * 通用占位符替换：将模板中的 {@code {key}} 替换为 {@code context} 中对应的值。
     * <p>
     * 未在上下文中出现的占位符将原样保留，{@code null} 模板返回空字符串。
     *
     * @param template 含占位符的模板
     * @param context  占位符键值对（可为 null）
     * @return 替换后的字符串
     */
    public String applyPlaceholders(String template, Map<String, String> context) {
        if (template == null) {
            return "";
        }
        if (context == null || context.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    @Override
    public void afterPropertiesSet() {
        // 如果没有配置模板，使用默认值（Req 6.3）
        if (core.isEmpty()) {
            log.info("未配置核心信息追问模板，使用默认模板");
            initDefaultCoreTemplates();
        }
        if (optional.isEmpty()) {
            log.info("未配置非核心信息追问模板，使用默认模板");
            initDefaultOptionalTemplates();
        }
        log.info("追问模板配置加载完成，核心模板：{} 个，非核心模板：{} 个",
                core.size(), optional.size());
    }

    /**
     * 热更新模板（Req 6.4）
     * <p>
     * 监听 {@link TemplateRefreshEvent}，重新载入默认模板中缺失的部分，
     * 无需重启服务即可生效。
     */
    @EventListener(TemplateRefreshEvent.class)
    public void refreshTemplates(TemplateRefreshEvent event) {
        log.info("收到模板刷新事件，开始热更新追问模板");
        afterPropertiesSet();
    }

    /**
     * 初始化默认核心模板
     */
    private void initDefaultCoreTemplates() {
        core.put("name", buildDefaultCoreTemplate("name"));
        core.put("contact", buildDefaultCoreTemplate("contact"));
        core.put("appointmentTime", buildDefaultCoreTemplate("appointmentTime"));
    }

    /**
     * 构建指定核心字段的默认模板
     *
     * @param fieldName 字段名（name / contact / appointmentTime）
     * @return 默认模板配置；未知字段返回 null
     */
    private TemplateConfig buildDefaultCoreTemplate(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        TemplateConfig config = new TemplateConfig();
        switch (fieldName) {
            case "name" -> {
                config.setFieldName("name");
                config.setDisplayName("姓名");
                config.setQuestion("请告诉我您的姓名，以便我们为您预约。");
                config.setValidationRegex("^[\\u4e00-\\u9fa5a-zA-Z]{2,20}$");
                config.setValidationMessage("请输入有效的姓名（2-20个字符，支持中文和英文）");
                config.setPriority(1);
            }
            case "contact" -> {
                config.setFieldName("contact");
                config.setDisplayName("联系方式");
                config.setQuestion("请提供您的联系方式（手机号或邮箱），以便我们与您确认预约。");
                config.setValidationRegex("^(1[3-9]\\d{9}|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})$");
                config.setValidationMessage("请输入有效的手机号或邮箱地址");
                config.setPriority(2);
            }
            case "appointmentTime" -> {
                config.setFieldName("appointmentTime");
                config.setDisplayName("预约时间");
                config.setQuestion("您希望预约什么时间？请提供具体日期和时间，例如：2024-01-15 14:00 或 明天下午3点");
                config.setValidationRegex(null);
                config.setValidationMessage("请提供有效的日期和时间");
                config.setPriority(3);
            }
            default -> {
                return null;
            }
        }
        return config;
    }

    /**
     * 初始化默认非核心模板
     */
    private void initDefaultOptionalTemplates() {
        TemplateConfig topicConfig = new TemplateConfig();
        topicConfig.setFieldName("topic");
        topicConfig.setDisplayName("咨询主题");
        topicConfig.setQuestion("请问您想咨询什么主题？（可选，如：职业规划、简历优化、薪资谈判等）");
        topicConfig.setPriority(4);
        optional.put("topic", topicConfig);

        TemplateConfig remarkConfig = new TemplateConfig();
        remarkConfig.setFieldName("remark");
        remarkConfig.setDisplayName("备注信息");
        remarkConfig.setQuestion("还有其他需要补充的信息吗？（可选）");
        remarkConfig.setPriority(5);
        optional.put("remark", remarkConfig);
    }
}
