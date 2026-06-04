package com.yupi.yuaiagent.agent;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Intent Enum Completeness
 *
 * <p>设计文档「8. Correctness Properties」中的 Property 2：
 * <em>For any valid AgentIntent value, the CONSULTATION enum constant SHALL exist with
 * agentName "预约咨询专家" and appropriate description.</em></p>
 *
 * <p>该属性测试验证意图枚举的完整性不变量：
 * <ul>
 *   <li>CONSULTATION 常量存在，agentName 为 "预约咨询专家"，描述非空；</li>
 *   <li>任意 AgentIntent 取值都拥有非空的 agentName 与 description；</li>
 *   <li>{@code fromRawIntent} 对任意输入都返回非 null 的意图（全函数）；</li>
 *   <li>{@code fromRawIntent} 能将每个枚举名称解析回其自身。</li>
 * </ul>
 *
 * <b>Validates: Requirements 1.2</b>
 */
class AgentIntentPropertyTest {

    /**
     * 完整性不变量：对任意 AgentIntent 取值，agentName 与 description 均非空。
     *
     * <b>Validates: Requirements 1.2</b>
     */
    @Property
    void everyIntentHasNonBlankAgentNameAndDescription(@ForAll AgentIntent intent) {
        assertThat(intent.getAgentName())
                .as("%s 的 agentName 应非空", intent)
                .isNotBlank();
        assertThat(intent.getDescription())
                .as("%s 的 description 应非空", intent)
                .isNotBlank();
    }

    /**
     * 解析自洽：对每个枚举值，fromRawIntent(枚举名) 应解析回该枚举自身。
     *
     * <b>Validates: Requirements 1.2</b>
     */
    @Property
    void fromRawIntentResolvesEachEnumNameToItself(@ForAll AgentIntent intent) {
        assertThat(AgentIntent.fromRawIntent(intent.name()))
                .as("fromRawIntent(\"%s\") 应解析回 %s", intent.name(), intent)
                .isEqualTo(intent);
    }

    /**
     * 全函数性：对任意字符串输入，fromRawIntent 永不返回 null。
     *
     * <b>Validates: Requirements 1.2</b>
     */
    @Property
    void fromRawIntentNeverReturnsNullForArbitraryInput(@ForAll String rawIntent) {
        assertThat(AgentIntent.fromRawIntent(rawIntent))
                .as("fromRawIntent 对任意输入都应返回非 null 意图")
                .isNotNull();
    }

    /**
     * CONSULTATION 常量存在性与具体取值（Requirement 1.2 的核心断言）。
     *
     * <b>Validates: Requirements 1.2</b>
     */
    @Example
    void consultationConstantExistsWithExpectedAgentName() {
        AgentIntent consultation = AgentIntent.CONSULTATION;
        assertThat(consultation).isNotNull();
        assertThat(consultation.getAgentName()).isEqualTo("预约咨询专家");
        assertThat(consultation.getDescription()).isNotBlank();
    }

    /**
     * 边界：null 与空白输入回退到 GENERAL，保证全函数性的边界正确。
     *
     * <b>Validates: Requirements 1.2</b>
     */
    @Example
    void fromRawIntentReturnsGeneralForNullOrBlankInput() {
        assertThat(AgentIntent.fromRawIntent(null)).isEqualTo(AgentIntent.GENERAL);
        assertThat(AgentIntent.fromRawIntent("")).isEqualTo(AgentIntent.GENERAL);
        assertThat(AgentIntent.fromRawIntent("   ")).isEqualTo(AgentIntent.GENERAL);
    }
}
