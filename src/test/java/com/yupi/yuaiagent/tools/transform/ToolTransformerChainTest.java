package com.yupi.yuaiagent.tools.transform;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolTransformerChainTest {

    @Test
    void rejectDoesNotInvokeDelegate() {
        ToolCallback delegate = delegate("searchWeb");
        TransformingToolCallback wrapped = new TransformingToolCallback(delegate, new UrlSafetyTransformer());
        String out = wrapped.call("open file://etc/passwd");
        assertThat(out).contains("rejected");
        verify(delegate, never()).call(anyString());
    }

    @Test
    void transformerExceptionIsFailClosed() {
        ToolTransformer boom = (name, input) -> {
            throw new IllegalStateException("boom");
        };
        ToolCallback delegate = delegate("searchWeb");
        String out = new TransformingToolCallback(delegate, new ToolTransformerChain(List.of(boom))).call("{}");
        assertThat(out).contains("rejected");
        verify(delegate, never()).call(anyString());
    }

    @Test
    void rewriteReachesDelegateOnce() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback delegate = delegate("searchWeb");
        when(delegate.call(anyString())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return "ok";
        });
        ToolTransformer rewrite = (name, input) -> TransformResult.rewrite("{\"q\":\"safe\"}");
        String out = new TransformingToolCallback(delegate, rewrite).call("{}");
        assertThat(out).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
        verify(delegate).call("{\"q\":\"safe\"}");
    }

    private static ToolCallback delegate(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(callback.getToolDefinition()).thenReturn(def);
        return callback;
    }
}
