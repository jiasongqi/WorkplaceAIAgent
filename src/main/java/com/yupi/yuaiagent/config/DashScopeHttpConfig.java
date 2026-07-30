package com.yupi.yuaiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Bounds DashScope / Spring AI RestClient HTTP timeouts so sync LLM calls
 * (e.g. query rewrite) cannot silent-hang until the 5-minute SSE timeout.
 * <p>
 * Streaming responses use WebClient separately; this primarily protects
 * blocking {@code call()} paths. Keep read-timeout below SSE emitter timeout.
 */
@Configuration
public class DashScopeHttpConfig {

    @Bean
    public RestClientCustomizer dashScopeRestClientCustomizer(
            @Value("${spring.ai.dashscope.http.connect-timeout:10s}") Duration connectTimeout,
            @Value("${spring.ai.dashscope.http.read-timeout:90s}") Duration readTimeout) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }
}
