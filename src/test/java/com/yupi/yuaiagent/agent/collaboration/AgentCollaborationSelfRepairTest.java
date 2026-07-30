package com.yupi.yuaiagent.agent.collaboration;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.agent.ResultAggregator;
import com.yupi.yuaiagent.agent.output.FormatterRegistry;
import com.yupi.yuaiagent.agent.reflexion.ReflexionService;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.metrics.AgentExecutionMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentCollaborationSelfRepairTest {

    @Mock
    private ArtifactShelf artifactShelf;
    @Mock
    private ReflexionService reflexionService;
    @Mock
    private AgentExecutionMetrics executionMetrics;

    private AgentCollaborationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        Executor direct = Runnable::run;
        coordinator = new AgentCollaborationCoordinator(
                new ResultAggregator(new FormatterRegistry(), null),
                artifactShelf,
                reflexionService,
                executionMetrics,
                direct);
        when(artifactShelf.put(any(Artifact.class))).thenAnswer(inv -> {
            Artifact a = inv.getArgument(0);
            a.setArtifactId("art_test");
            return new ArtifactShelf.PutResult(true, "art_test", null, a);
        });
    }

    @Test
    void qualityNack_selfRepairsBeforeFailover() throws Exception {
        AtomicInteger resumeCalls = new AtomicInteger();
        CollaborationResult result = coordinator.failoverAfterQuality(
                AgentIntent.RESUME,
                "incomplete",
                List.of("缺量化成果"),
                List.of("补 STAR 指标"),
                "帮我改简历",
                "chat-1",
                "user-1",
                (intent, injection) -> {
                    if (intent == AgentIntent.RESUME) {
                        resumeCalls.incrementAndGet();
                        assertThat(injection).contains("Handoff NACK");
                        assertThat(injection).contains("自我修复");
                        return "这是一段足够长的简历修复建议，包含项目量化成果与 STAR 表达示例，满足自我修复长度门槛，可直接交给用户。";
                    }
                    return "不应调用 GENERAL";
                },
                List.of());

        assertThat(result.usedSelfRepair()).isTrue();
        assertThat(result.mode()).isEqualTo(CollaborationResult.Mode.SELF_REPAIR);
        assertThat(result.effectiveIntent()).isEqualTo(AgentIntent.RESUME);
        assertThat(result.failoverIntent()).isNull();
        assertThat(resumeCalls.get()).isEqualTo(1);
        assertThat(result.finalAnswer()).contains("STAR");
    }

    @Test
    void weakSelfRepair_escalatesToGeneral() throws Exception {
        AtomicInteger generalCalls = new AtomicInteger();
        CollaborationResult result = coordinator.failoverAfterQuality(
                AgentIntent.NEGOTIATION,
                "too short",
                null,
                null,
                "谈薪",
                "chat-2",
                "user-2",
                (intent, injection) -> {
                    if (intent == AgentIntent.NEGOTIATION) {
                        return "短"; // well below repair threshold → escalate
                    }
                    generalCalls.incrementAndGet();
                    return "通用顾问接手后的完整谈薪建议，给出区间与话术，长度足够通过验收门槛。";
                },
                List.of());

        assertThat(result.usedFailover()).isTrue();
        assertThat(result.effectiveIntent()).isEqualTo(AgentIntent.GENERAL);
        assertThat(generalCalls.get()).isEqualTo(1);
    }
}
