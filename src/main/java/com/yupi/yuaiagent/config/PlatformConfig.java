package com.yupi.yuaiagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.agent.prompt.MapSectionContributor;
import com.yupi.yuaiagent.agent.prompt.PromptSectionRenderer;
import com.yupi.yuaiagent.agent.runner.UnsupportedConsultationRunner;
import com.yupi.yuaiagent.guard.PromptInjectionDetector;
import com.yupi.yuaiagent.history.ActionHistoryDualWriter;
import com.yupi.yuaiagent.observability.ObservabilityExporter;
import com.yupi.yuaiagent.observability.ObservabilityExporterBus;
import com.yupi.yuaiagent.pack.ExpertPackPreferenceRepository;
import com.yupi.yuaiagent.pack.FileExpertPackPreferenceRepository;
import com.yupi.yuaiagent.pack.JdbcExpertPackPreferenceRepository;
import com.yupi.yuaiagent.permission.ActivationFingerprintCache;
import com.yupi.yuaiagent.skill.SkillOverlayResolver;
import com.yupi.yuaiagent.skill.SkillOverlayStore;
import com.yupi.yuaiagent.tools.transform.PathConstraintTransformer;
import com.yupi.yuaiagent.tools.transform.ToolTransformerChain;
import com.yupi.yuaiagent.tools.transform.TransformingToolCallbackProvider;
import com.yupi.yuaiagent.tools.transform.UrlSafetyTransformer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class PlatformConfig {

    @Bean
    public UnsupportedConsultationRunner unsupportedConsultationRunner() {
        return new UnsupportedConsultationRunner();
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.pack", name = "storage", havingValue = "file", matchIfMissing = true)
    public ExpertPackPreferenceRepository fileExpertPackPreferenceRepository(
            @Value("${expert-pack.storage.dir:./tmp/expert-packs}") String storageDir,
            ObjectMapper objectMapper) {
        return new FileExpertPackPreferenceRepository(Path.of(storageDir).resolve("user-prefs.json"), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.pack", name = "storage", havingValue = "jdbc")
    public ExpertPackPreferenceRepository jdbcExpertPackPreferenceRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcExpertPackPreferenceRepository(jdbcTemplate);
    }

    @Bean
    public ActivationFingerprintCache activationFingerprintCache() {
        return new ActivationFingerprintCache();
    }

    @Bean
    public ToolTransformerChain toolTransformerChain() {
        return new ToolTransformerChain(List.of(new UrlSafetyTransformer(), new PathConstraintTransformer()));
    }

    @Bean
    public PromptSectionRenderer promptSectionRenderer() {
        return new PromptSectionRenderer(List.of(
                new MapSectionContributor("goal"),
                new MapSectionContributor("profile"),
                new MapSectionContributor("companion"),
                new MapSectionContributor("digitalEmployee"),
                new MapSectionContributor("sharedState"),
                new MapSectionContributor("artifact"),
                new MapSectionContributor("hybrid"),
                new MapSectionContributor("crossAgent"),
                new MapSectionContributor("reflexion")
        ));
    }

    @Bean
    public SkillOverlayStore skillOverlayStore() {
        return new SkillOverlayStore();
    }

    @Bean
    public SkillOverlayResolver skillOverlayResolver(PromptInjectionDetector detector) {
        return new SkillOverlayResolver(detector);
    }

    @Bean
    public ObservabilityExporterBus observabilityExporterBus(
            ObjectProvider<ObservabilityExporter> exporters) {
        return new ObservabilityExporterBus(exporters.orderedStream().toList());
    }

    @Bean
    public ActionHistoryDualWriter actionHistoryDualWriter() {
        return new ActionHistoryDualWriter();
    }

    @Bean
    @ConditionalOnProperty(name = "platform.tool-transformer.enabled", havingValue = "true")
    public org.springframework.beans.factory.config.BeanPostProcessor transformingToolCallbackProviderPostProcessor(
            ToolTransformerChain chain) {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof org.springframework.ai.tool.ToolCallbackProvider provider
                        && !(bean instanceof TransformingToolCallbackProvider)) {
                    return new TransformingToolCallbackProvider(provider, chain);
                }
                return bean;
            }
        };
    }
}
