package com.yupi.yuaiagent.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.agent.model.Appointment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉日历服务实现
 * 对接钉钉开放平台 API 创建日历事件
 * 
 * @author jsq
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "calendar.provider", havingValue = "DINGTALK")
public class DingTalkCalendarService implements CalendarService {

    @Value("${calendar.dingtalk.app-key:}")
    private String appKey;

    @Value("${calendar.dingtalk.app-secret:}")
    private String appSecret;

    @Value("${calendar.dingtalk.base-url:https://api.dingtalk.com}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DingTalkCalendarService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CalendarEvent createEvent(Appointment appointment) throws CalendarException {
        try {
            // 1. 获取 access_token
            String accessToken = getAccessToken();

            // 2. 创建日历事件
            String url = baseUrl + "/v1.0/calendar/events";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            // 构建请求体
            Map<String, Object> requestBody = buildEventRequest(appointment);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 3. 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // 4. 解析响应
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseEventResponse(response.getBody(), appointment);
            } else {
                throw new CalendarException("创建钉钉日历事件失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("钉钉日历服务异常", e);
            throw new CalendarException("钉钉日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public CalendarEvent updateEvent(String eventId, Appointment appointment) throws CalendarException {
        try {
            String accessToken = getAccessToken();
            String url = baseUrl + "/v1.0/calendar/events/" + eventId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = buildEventRequest(appointment);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseEventResponse(response.getBody(), appointment);
            } else {
                throw new CalendarException("更新钉钉日历事件失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("钉钉日历服务异常", e);
            throw new CalendarException("钉钉日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelEvent(String eventId) throws CalendarException {
        try {
            String accessToken = getAccessToken();
            String url = baseUrl + "/v1.0/calendar/events/" + eventId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new CalendarException("取消钉钉日历事件失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("钉钉日历服务异常", e);
            throw new CalendarException("钉钉日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean checkAvailability(LocalDateTime start, LocalDateTime end) throws CalendarException {
        try {
            String accessToken = getAccessToken();
            String url = baseUrl + "/v1.0/calendar/users/me/querySchedule";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("startTime", start.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            requestBody.put("endTime", end.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode scheduleList = jsonNode.path("scheduleInformation");
                // 无日程信息表示该时间段可用
                return !scheduleList.isArray() || scheduleList.isEmpty();
            } else {
                throw new CalendarException("查询钉钉忙闲状态失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("钉钉日历服务异常", e);
            throw new CalendarException("钉钉日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public Appointment.CalendarProvider getProvider() {
        return Appointment.CalendarProvider.DINGTALK;
    }

    /**
     * 获取 access_token
     */
    private String getAccessToken() throws CalendarException {
        try {
            String url = "https://api.dingtalk.com/v1.0/oauth2/accessToken";

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("appKey", appKey);
            requestBody.put("appSecret", appSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String accessToken = jsonNode.path("accessToken").asText();
                if (accessToken != null && !accessToken.isEmpty()) {
                    return accessToken;
                } else {
                    throw new CalendarException("获取钉钉 access token 失败");
                }
            } else {
                throw new CalendarException("获取钉钉 access token 失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取钉钉 access token 异常", e);
            throw new CalendarException("获取钉钉 access token 异常: " + e.getMessage(), e);
        }
    }

    /**
     * 构建事件请求体
     */
    private Map<String, Object> buildEventRequest(Appointment appointment) {
        Map<String, Object> request = new HashMap<>();

        // 事件摘要
        String summary = "预约咨询";
        if (appointment.getTopic() != null && !appointment.getTopic().isEmpty()) {
            summary += " - " + appointment.getTopic();
        }
        request.put("summary", summary);

        // 事件描述
        StringBuilder description = new StringBuilder();
        description.append("预约人：").append(appointment.getName()).append("\n");
        description.append("联系方式：").append(appointment.getContact()).append("\n");
        if (appointment.getTopic() != null && !appointment.getTopic().isEmpty()) {
            description.append("咨询主题：").append(appointment.getTopic()).append("\n");
        }
        if (appointment.getRemark() != null && !appointment.getRemark().isEmpty()) {
            description.append("备注：").append(appointment.getRemark()).append("\n");
        }
        request.put("description", description.toString());

        // 开始时间（ISO 8601 格式）
        LocalDateTime startTime = appointment.getAppointmentTime();
        String startTimeStr = startTime.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, String> start = new HashMap<>();
        start.put("dateTime", startTimeStr);
        start.put("timeZone", "Asia/Shanghai");
        request.put("start", start);

        // 结束时间（默认1小时后）
        LocalDateTime endTime = startTime.plusHours(1);
        String endTimeStr = endTime.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, String> end = new HashMap<>();
        end.put("dateTime", endTimeStr);
        end.put("timeZone", "Asia/Shanghai");
        request.put("end", end);

        // 参与者
        if (appointment.getContact() != null && appointment.getContact().contains("@")) {
            Map<String, String> attendee = new HashMap<>();
            attendee.put("email", appointment.getContact());
            request.put("attendees", new Map[]{attendee});
        }

        return request;
    }

    /**
     * 解析事件响应
     */
    private CalendarEvent parseEventResponse(String responseBody, Appointment appointment) throws CalendarException {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);

            return CalendarEvent.builder()
                    .eventId(jsonNode.path("eventId").asText())
                    .title(jsonNode.path("summary").asText())
                    .description(jsonNode.path("description").asText())
                    .startTime(appointment.getAppointmentTime())
                    .endTime(appointment.getAppointmentTime().plusHours(1))
                    .link(jsonNode.path("htmlLink").asText())
                    .provider("DINGTALK")
                    .build();
        } catch (Exception e) {
            log.error("解析钉钉响应异常", e);
            throw new CalendarException("解析钉钉响应异常: " + e.getMessage(), e);
        }
    }
}
