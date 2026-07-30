package com.yupi.yuaiagent.workflow.dag;

import com.yupi.yuaiagent.agent.AgentRunner;
import com.yupi.yuaiagent.agent.ResultAggregator;
import com.yupi.yuaiagent.agent.output.FormatterRegistry;
import com.yupi.yuaiagent.agent.output.TextOutput;
import com.yupi.yuaiagent.agent.task.FailurePolicy;
import com.yupi.yuaiagent.budget.TokenUsage;
import com.yupi.yuaiagent.context.ConversationContext;
import com.yupi.yuaiagent.workflow.runtime.WorkflowRepository;
import com.yupi.yuaiagent.workflow.runtime.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagWorkflowExecutorTest {

    @TempDir
    Path tempDir;

    private DagWorkflowExecutor executor;
    private final AtomicInteger resumeCalls = new AtomicInteger();
    private final AtomicInteger negoCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        WorkflowRepository repo = new WorkflowRepository();
        ReflectionTestUtils.setField(repo, "storageDir", tempDir.resolve("wf").toString());
        repo.init();
        ResultAggregator aggregator = new ResultAggregator(new FormatterRegistry(), null);
        Executor pool = Executors.newFixedThreadPool(4);
        executor = new DagWorkflowExecutor(repo, aggregator, pool);

        AgentRunner resume = new AgentRunner() {
            @Override
            public com.yupi.yuaiagent.agent.output.AgentOutput run(
                    ConversationContext ctx, String msg) {
                resumeCalls.incrementAndGet();
                return new TextOutput("简历建议", List.of());
            }

            @Override
            public TokenUsage getLastTokenUsage() {
                return TokenUsage.ZERO;
            }
        };
        AgentRunner nego = new AgentRunner() {
            @Override
            public com.yupi.yuaiagent.agent.output.AgentOutput run(
                    ConversationContext ctx, String msg) {
                negoCalls.incrementAndGet();
                return new TextOutput("薪资建议", List.of());
            }

            @Override
            public TokenUsage getLastTokenUsage() {
                return TokenUsage.ZERO;
            }
        };
        AgentRunner general = new AgentRunner() {
            @Override
            public com.yupi.yuaiagent.agent.output.AgentOutput run(
                    ConversationContext ctx, String msg) {
                return new TextOutput("通用建议", List.of());
            }

            @Override
            public TokenUsage getLastTokenUsage() {
                return TokenUsage.ZERO;
            }
        };
        executor.setAgentRunners(Map.of(
                "RESUME", resume,
                "NEGOTIATION", nego,
                "GENERAL", general
        ));
    }

    @Test
    void agentNodesInvokedAndSynthesizeMerges() {
        DagDefinition dag = new DagDefinition("JOB_CHANGE", FailurePolicy.RETRY_THEN_SKIP, List.of(
                DagNodeSpec.agent("resume", "RESUME", "简历优化", List.of()),
                DagNodeSpec.agent("nego", "NEGOTIATION", "薪资分析", List.of()),
                DagNodeSpec.synthesize("synthesize", List.of("resume", "nego"))
        ));
        dag.validate();

        ConversationContext ctx = new ConversationContext("", "", List.of(), "chat-1", "");
        DagExecutionResult result = executor.execute(dag, ctx, "我想跳槽并谈薪", "u1", "chat-1",
                DagProgressListener.noop());

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        assertEquals(1, resumeCalls.get());
        assertEquals(1, negoCalls.get());
        assertTrue(result.success());
        assertTrue(result.finalAnswer().contains("简历建议")
                || result.finalAnswer().contains("薪资建议")
                || result.finalAnswer().contains("综合"));
        assertEquals(2, result.opinions().stream().filter(o -> o.success()).count());
    }

    @Test
    void cycleRejectedBeforeRun() {
        DagDefinition dag = new DagDefinition("BAD", FailurePolicy.FAIL_FAST, List.of(
                DagNodeSpec.agent("a", "RESUME", "A", List.of("b")),
                DagNodeSpec.agent("b", "NEGOTIATION", "B", List.of("a"))
        ));
        ConversationContext ctx = new ConversationContext("", "", List.of(), "c", "");
        try {
            executor.execute(dag, ctx, "x", "u", "c", DagProgressListener.noop());
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("cycle"));
        }
        assertEquals(0, resumeCalls.get());
    }
}
