package com.yupi.yuaiagent.rag.hybrid;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextFirstHybridRetrievalTest {

    @Test
    void ranksTextAndLimitsVision() {
        TextFirstHybridRetrieval strategy = new TextFirstHybridRetrieval();
        var bundle = strategy.retrieve(
                "涨薪谈判",
                List.of(
                        new HybridRetrievalStrategy.TextHit("无关天气", 0, "a"),
                        new HybridRetrievalStrategy.TextHit("涨薪谈判话术", 0, "b")
                ),
                List.of(
                        new HybridRetrievalStrategy.VisionRef("薪资表截图", "img://1", 0, "v1"),
                        new HybridRetrievalStrategy.VisionRef("风景", "img://2", 0, "v2")
                ),
                1, 1);
        assertThat(bundle.textHits()).hasSize(1);
        assertThat(bundle.textHits().get(0).sourceId()).isEqualTo("b");
        assertThat(bundle.visionRefs()).hasSize(1);
        assertThat(bundle.toPromptContext(1)).contains("混合检索");
    }
}
