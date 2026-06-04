package com.yupi.yuaiagent.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.agent.model.Appointment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 飞书日历服务实现
 * 对接飞书开放平台 API 创建日历事件
 * 
 * @author jsq
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "calendar.provider", havingValue = "FEISHU")
public class FeishuCalendarService implements CalendarService {

    @Value("${calendar.feishu.app-id:}")
    private String appId;

    @Value("${calendar.feishu.app-secret:}")
    private String appSecret;

    @Value("${calendar.feishu.base-url:https://open.feishu.cn/open-apis}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FeishuCalendarService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public CalendarEvent createEvent(Appointment appointment) throws CalendarException {
        try {
            // 1. 获取 tenant_access_token
            String accessToken = getTenantAccessToken();

            // 2. 创建日历事件
            String url = baseUrl + "/calendar/v4/calendars/primary/events";

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
                throw new CalendarException("创建飞书日历事件失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("飞书日历服务异常", e);
            throw new CalendarException("飞书日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public CalendarEvent updateEvent(String eventId, Appointment appointment) throws CalendarException {
        try {
            String accessToken = getTenantAccessToken();
            String url = baseUrl + "/calendar/v4/calendars/primary/events/" + eventId;

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
                throw new CalendarException("更新飞书日历事件失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("飞书日历服务异常", e);
            throw new CalendarException("飞书日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelEvent(String eventId) throws CalendarException {
        try {
            String accessToken = getTenantAccessToken();
            String url = baseUrl + "/calendar/v4/calendars/primary/events/" + eventId;

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
                throw new CalendarException("取消飞书日历事件失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("飞书日历服务异常", e);
            throw new CalendarException("飞书日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean checkAvailability(LocalDateTime start, LocalDateTime end) throws CalendarException {
        try {
            String accessToken = getTenantAccessToken();
            String url = baseUrl + "/calendar/v4/freebusy/list";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("time_min", start.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            requestBody.put("time_max", end.atZone(ZoneId.systemDefault())
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
                if (jsonNode.path("code").asInt() != 0) {
                    throw new CalendarException("查询飞书忙闲状态失败: " + jsonNode.path("msg").asText());
                }
                JsonNode busyList = jsonNode.path("data").path("freebusy_list");
                // 无忙碌时间段表示该时间段可用
                return !busyList.isArray() || busyList.isEmpty();
            } else {
                throw new CalendarException("查询飞书忙闲状态失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("飞书日历服务异常", e);
            throw new CalendarException("飞书日历服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public Appointment.CalendarProvider getProvider() {
        return Appointment.CalendarProvider.FEISHU;
    }

    /**
     * 获取 tenant_access_token
     */
    private String getTenantAccessToken() throws CalendarException {
        try {
            String url = baseUrl + "/auth/v3/tenant_access_token/internal";

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("app_id", appId);
            requestBody.put("app_secret", appSecret);

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
                int code = jsonNode.path("code").asInt();
                if (code == 0) {
                    return jsonNode.path("tenant_access_token").asText();
                } else {
                    throw new CalendarException("获取飞书 access token 失败: " + jsonNode.path("msg").asText());
                }
            } else {
                throw new CalendarException("获取飞书 access token 失败: " + response.getStatusCode());
            }
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取飞书 access token 异常", e);
            throw new CalendarException("获取飞书 access token 异常: " + e.getMessage(), e);
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

        // 开始时间（时间戳，秒）
        LocalDateTime startTime = appointment.getAppointmentTime();
        long startTimestamp = startTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        Map<String, Long> start = new HashMap<>();
        start.put("timestamp", startTimestamp);
        request.put("start_time", start);

        // 结束时间（默认1小时后）
        LocalDateTime endTime = startTime.plusHours(1);
        long endTimestamp = endTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        Map<String, Long> end = new HashMap<>();
        end.put("timestamp", endTimestamp);
        request.put("end_time", end);

        // 参与者
        if (appointment.getContact() != null && appointment.getContact().contains("@")) {
            Map<String, String> attendee = new HashMap<>();
            attendee.put("type", "third_party");
            attendee.put("third_party_email", appointment.getContact());
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
            int code = jsonNode.path("code").asInt();

            if (code != 0) {
                throw new CalendarException("飞书 API 返回错误: " + jsonNode.path("msg").asText());
            }

            JsonNode data = jsonNode.path("data").path("event");

            return CalendarEvent.builder()
                    .eventId(data.path("event_id").asText())
                    .title(data.path("summary").asText())
                    .description(data.path("description").asText())
                    .startTime(appointment.getAppointmentTime())
                    .endTime(appointment.getAppointmentTime().plusHours(1))
                    .link(data.path("html_link").asText())
                    .provider("FEISHU")
                    .build();
        } catch (CalendarException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析飞书响应异常", e);
            throw new CalendarException("解析飞书响应异常: " + e.getMessage(), e);
        }
    }
}
