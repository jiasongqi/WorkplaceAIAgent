package com.yupi.yuaiagent.memory;

import com.yupi.yuaiagent.memory.context.KeyInfoExtractor;
import com.yupi.yuaiagent.memory.summary.SummaryLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExperienceQueryBuilderTest {

    @Mock
    private SummaryLayer summaryLayer;

    private ExperienceQueryBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ExperienceQueryBuilder(new KeyInfoExtractor(), summaryLayer);
    }

    @Test
    void prefersCurrentMessageOverSummary() {
        String q = builder.build("u1", "昨天安装的 Gearbox-X1 螺丝扭矩是多少");
        assertTrue(q.contains("Gearbox") || q.contains("扭矩") || q.contains("螺丝"));
        verify(summaryLayer, never()).getRecentSummaries(anyString(), anyInt());
    }

    @Test
    void fallsBackToSummaryWhenMessageEmpty() {
        when(summaryLayer.getRecentSummaries("u1", 200)).thenReturn("【近期对话摘要】 话题: 零件A安装");
        String q = builder.build("u1", null);
        assertTrue(q.contains("零件A") || q.contains("摘要"));
    }
}
