package com.yupi.yuaiagent.calendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 日历事件返回结构
 * 封装企业日历（飞书/钉钉）创建或更新事件后返回的统一结果，
 * 包含事件 ID、日历链接等关键信息。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    /**
     * 日历事件 ID（由企业日历平台返回）
     */
    private String eventId;

    /**
     * 事件标题
     */
    private String title;

    /**
     * 事件描述
     */
    private String description;

    /**
     * 事件开始时间
     */
    private LocalDateTime startTime;

    /**
     * 事件结束时间
     */
    private LocalDateTime endTime;

    /**
     * 日历事件链接（用户可点击查看的日历地址）
     */
    private String link;

    /**
     * 创建该事件的日历服务提供商（FEISHU / DINGTALK）
     */
    private String provider;
}
