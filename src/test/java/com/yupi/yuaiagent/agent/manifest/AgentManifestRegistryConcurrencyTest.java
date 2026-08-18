package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentManifestRegistryConcurrencyTest {

    @Test
    void reloadDoesNotResetFeedbackBoost() throws InterruptedException {
        AgentManifestRegistry registry = new AgentManifestRegistry();
        registry.penalize(AgentIntent.RESUME, 0.3);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < 8; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int n = 0; n < 200; n++) {
                        registry.rank("简历 求职");
                    }
                } catch (Exception ex) {
                    failures.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(failures.get()).isZero();

        AgentDescriptor resume = AgentDescriptor.builder()
                .agentCode("resume-agent")
                .intent("RESUME")
                .displayName("简历优化专家")
                .description("擅长简历优化、求职、面试技巧、offer 选择。输入需要经历/目标岗位描述。")
                .routingKeywords(List.of("简历", "求职", "面试", "offer", "投递", "岗位"))
                .inputRequirements(List.of("text"))
                .build();
        registry.reloadFrom(List.of(resume));

        var ranked = registry.rank("简历 求职");
        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).manifest().intent()).isEqualTo(AgentIntent.RESUME);
        assertThat(ranked.get(0).score()).isCloseTo(2.0 * 0.3, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void disabledYamlAgentDoesNotKeepLegacyRouteInRegistryMode() {
        AgentDescriptor disabled = AgentDescriptor.builder()
                .agentCode("negotiation-agent")
                .intent("NEGOTIATION")
                .displayName("薪资谈判专家")
                .description("擅长谈薪、涨薪、薪酬分析与话术。输入需要当前薪资/期望。")
                .routingKeywords(List.of("谈薪", "涨薪", "薪资"))
                .inputRequirements(List.of("text"))
                .enabled(false)
                .build();
        Map<AgentIntent, AgentManifest> derived = AgentManifestFactory.fromDescriptors(List.of(disabled));
        assertThat(derived).doesNotContainKey(AgentIntent.NEGOTIATION);

        AgentManifestRegistry registry = new AgentManifestRegistry(
                AgentMetadataProperties.Source.REGISTRY, List.of(disabled));
        assertThat(registry.suggest("帮我谈薪涨薪", 0.9)).isNull();
    }
}
