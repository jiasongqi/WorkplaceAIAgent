package com.yupi.yuaiagent.memory.fact;

/**
 * 事实类别枚举 — 用于 Fact_Store 中 FactEntry 的分类
 *
 * <p>每条用户事实归属于一个类别，便于检索、展示和 Token 预算裁剪时按类别优先级排序。
 */
public enum FactCategory {

    /** 身份信息：姓名、年龄、所在城市等 */
    IDENTITY("身份信息"),

    /** 职业信息：行业、岗位、公司、工作年限等 */
    CAREER("职业信息"),

    /** 偏好设定：沟通风格、语言、格式偏好等 */
    PREFERENCES("偏好设定"),

    /** 目标计划：短期/长期职业目标、发展方向等 */
    GOALS("目标计划"),

    /** 约束条件：薪资底线、地域限制、时间约束等 */
    CONSTRAINTS("约束条件");

    private final String displayName;

    FactCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取类别的中文显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 从字符串解析 FactCategory，忽略大小写
     *
     * @param value 类别名称字符串
     * @return 对应的枚举值，未匹配返回 null
     */
    public static FactCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
