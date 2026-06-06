package com.yupi.yuaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;

/**
 * @author jsq
 */
@SpringBootApplication(exclude = {
        // 为了便于大家开发调试和部署，取消数据库自动配置，需要使用 PgVector 时把 DataSourceAutoConfiguration.class 删除
        DataSourceAutoConfiguration.class,
        // 项目使用 DashScope，排除 Ollama 自动配置避免 ChatModel bean 冲突
        OllamaChatAutoConfiguration.class
})
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }

}
