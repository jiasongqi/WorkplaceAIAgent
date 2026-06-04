package com.yupi.yuaiagent.agent.model;

/**
 * 核心信息类型枚举
 * 定义预约咨询所需收集的核心信息类型
 *
 * @author jsq
 */
public enum CoreInfoType {

    /**
     * 预约人姓名
     */
    NAME("name", "您的姓名"),

    /**
     * 联系方式（手机/邮箱）
     */
    CONTACT("contact", "联系方式"),

    /**
     * 期望预约时间
     */
    APPOINTMENT_TIME("appointmentTime", "预约时间");

    /**
     * 对应 FollowUpQuestion / CoreInformation 中的字段名
     */
    private final String fieldName;

    /**
     * 显示名称
     */
    private final String displayName;

    CoreInfoType(String fieldName, String displayName) {
        this.fieldName = fieldName;
        this.displayName = displayName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据字段名查找对应的核心信息类型
     *
     * @param fieldName 字段名（如 name、contact、appointmentTime）
     * @return 匹配的核心信息类型，未匹配时返回 null
     */
    public static CoreInfoType fromFieldName(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        for (CoreInfoType type : values()) {
            if (type.fieldName.equalsIgnoreCase(fieldName)) {
                return type;
            }
        }
        return null;
    }
}
