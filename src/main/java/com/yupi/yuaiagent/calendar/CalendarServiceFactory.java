package com.yupi.yuaiagent.calendar;

import com.yupi.yuaiagent.agent.model.Appointment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日历服务工厂
 * 根据配置选择合适的日历服务实现
 * 
 * @author jsq
 */
@Slf4j
@Component
public class CalendarServiceFactory {

    @Value("${calendar.provider:FEISHU}")
    private String providerName;

    private final Map<Appointment.CalendarProvider, CalendarService> serviceMap;

    public CalendarServiceFactory(List<CalendarService> services) {
        this.serviceMap = services.stream()
                .collect(Collectors.toMap(
                        CalendarService::getProvider,
                        Function.identity()
                ));
        log.info("日历服务工厂初始化，可用服务：{}", serviceMap.keySet());
    }

    /**
     * 获取日历服务
     * 
     * @return 日历服务实现
     */
    public CalendarService getCalendarService() {
        Appointment.CalendarProvider provider;
        try {
            provider = Appointment.CalendarProvider.valueOf(providerName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知的日历服务提供商：{}，使用默认飞书服务", providerName);
            provider = Appointment.CalendarProvider.FEISHU;
        }
        return getCalendarService(provider);
    }

    /**
     * 获取指定的日历服务
     * 
     * @param provider 服务提供商
     * @return 日历服务实现
     */
    public CalendarService getCalendarService(Appointment.CalendarProvider provider) {
        CalendarService service = serviceMap.get(provider);
        if (service == null) {
            throw new IllegalStateException("未找到日历服务实现：" + provider);
        }
        return service;
    }

    /**
     * 检查服务是否可用
     */
    public boolean isServiceAvailable(Appointment.CalendarProvider provider) {
        return serviceMap.containsKey(provider);
    }

    /**
     * 获取所有可用的服务提供商
     */
    public List<Appointment.CalendarProvider> getAvailableProviders() {
        return List.copyOf(serviceMap.keySet());
    }
}
