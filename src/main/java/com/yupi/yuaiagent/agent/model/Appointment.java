package com.yupi.yuaiagent.agent.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 预约记录实体
 * 包含预约人信息、时间、状态等
 * 
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    /**
     * 预约 ID
     */
    private String appointmentId;

    /**
     * 会话 ID
     */
    private String chatId;

    /**
     * 预约人姓名
     */
    private String name;

    /**
     * 联系方式（手机/邮箱）
     */
    private String contact;

    /**
     * 预约时间
     */
    private LocalDateTime appointmentTime;

    /**
     * 咨询主题
     */
    private String topic;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 日历事件 ID
     */
    private String calendarEventId;

    /**
     * 日历链接
     */
    private String calendarLink;

    /**
     * 日历服务提供商
     */
    private CalendarProvider calendarProvider;

    /**
     * 预约状态
     */
    private AppointmentStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 预约状态枚举
     */
    public enum AppointmentStatus {
        PENDING("待确认"),
        CONFIRMED("已确认"),
        CANCELLED("已取消"),
        COMPLETED("已完成");

        private final String description;

        AppointmentStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 日历服务提供商枚举
     */
    public enum CalendarProvider {
        FEISHU("飞书"),
        DINGTALK("钉钉");

        private final String description;

        CalendarProvider(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
