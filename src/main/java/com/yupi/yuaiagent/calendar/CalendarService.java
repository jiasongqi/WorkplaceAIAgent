package com.yupi.yuaiagent.calendar;

import com.yupi.yuaiagent.agent.model.Appointment;

import java.time.LocalDateTime;

/**
 * 日历服务统一接口
 * 抽象飞书、钉钉等企业日历平台的差异，提供统一的预约事件管理能力。
 * 具体实现通过 {@code calendar.provider} 配置选择（FEISHU / DINGTALK）。
 *
 * @author jsq
 */
public interface CalendarService {

    /**
     * 创建预约事件
     *
     * @param appointment 预约信息（包含预约人、时间、主题等）
     * @return 创建结果，包含事件 ID 和日历链接
     * @throws CalendarException 当日历 API 调用失败时抛出
     */
    CalendarEvent createEvent(Appointment appointment) throws CalendarException;

    /**
     * 取消预约事件
     *
     * @param eventId 日历事件 ID
     * @throws CalendarException 当日历 API 调用失败时抛出
     */
    void cancelEvent(String eventId) throws CalendarException;

    /**
     * 修改预约事件
     *
     * @param eventId     日历事件 ID
     * @param appointment 新的预约信息
     * @return 修改后的事件结果，包含事件 ID 和日历链接
     * @throws CalendarException 当日历 API 调用失败时抛出
     */
    CalendarEvent updateEvent(String eventId, Appointment appointment) throws CalendarException;

    /**
     * 检查时间段是否可用
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 时间段可用返回 true，否则返回 false
     * @throws CalendarException 当日历 API 调用失败时抛出
     */
    boolean checkAvailability(LocalDateTime startTime, LocalDateTime endTime) throws CalendarException;

    /**
     * 获取当前日历服务提供商类型
     *
     * @return 日历服务提供商（FEISHU / DINGTALK）
     */
    Appointment.CalendarProvider getProvider();

    /**
     * 日历服务异常
     * 当企业日历 API 调用失败（创建、取消、修改事件或可用性检查）时抛出，
     * 由上层捕获后记录错误日志并向用户返回友好提示。
     */
    class CalendarException extends RuntimeException {

        public CalendarException(String message) {
            super(message);
        }

        public CalendarException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
