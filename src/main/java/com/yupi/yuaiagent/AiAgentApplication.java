package com.yupi.yuaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author jsq
 */
@EnableAsync
@SpringBootApplication(exclude = {
        // DashScope only — exclude Ollama to avoid ChatModel bean conflict
        OllamaChatAutoConfiguration.class
})
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }

}