package com.yupi.yuaiagent.agent.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 核心预约信息
 * 包含预约咨询所需的必要信息
 * 
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreInformation {

    /**
     * 预约人姓名
     */
    private String name;

    /**
     * 联系方式（手机/邮箱）
     */
    private String contact;

    /**
     * 期望预约时间
     */
    private LocalDateTime appointmentTime;

    /**
     * 咨询主题（非核心）
     */
    private String topic;

    /**
     * 备注信息（非核心）
     */
    private String remark;

    /**
     * 检查核心信息是否完整
     */
    public boolean isComplete() {
        return name != null && !name.isEmpty()
                && contact != null && !contact.isEmpty()
                && appointmentTime != null;
    }

    /**
     * 获取缺失的核心信息字段
     */
    public List<String> getMissingFields() {
        List<String> missing = new ArrayList<>();
        if (name == null || name.isEmpty()) {
            missing.add("name");
        }
        if (contact == null || contact.isEmpty()) {
            missing.add("contact");
        }
        if (appointmentTime == null) {
            missing.add("appointmentTime");
        }
        return missing;
    }

    /**
     * 转换为 Appointment 实体
     */
    public Appointment toAppointment(String chatId, Appointment.CalendarProvider provider) {
        return Appointment.builder()
                .chatId(chatId)
                .name(name)
                .contact(contact)
                .appointmentTime(appointmentTime)
                .topic(topic)
                .remark(remark)
                .calendarProvider(provider)
                .status(Appointment.AppointmentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 生成确认信息文本
     */
    public String toConfirmationText() {
        StringBuilder sb = new StringBuilder();
        sb.append("请确认以下预约信息：\n\n");
        sb.append("姓名：").append(name).append("\n");
        sb.append("联系方式：").append(contact).append("\n");
        sb.append("预约时间：").append(appointmentTime != null ? appointmentTime.toString() : "未指定").append("\n");
        if (topic != null && !topic.isEmpty()) {
            sb.append("咨询主题：").append(topic).append("\n");
        }
        if (remark != null && !remark.isEmpty()) {
            sb.append("备注：").append(remark).append("\n");
        }
        sb.append("\n请回复「确认」创建预约，或回复「修改」重新填写信息。");
        return sb.toString();
    }
}
