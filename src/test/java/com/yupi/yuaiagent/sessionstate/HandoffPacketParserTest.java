package com.yupi.yuaiagent.sessionstate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandoffPacketParserTest {

    @Test
    void stripsMarkdownFenceAndProse() {
        String raw = """
                这是模型输出：
                ```json
                {"meta":{"from":"A","to":"B"},"mission":{"objective":"x"}}
                ```
                以上是交接包。
                """;
        String cleaned = HandoffPacketParser.extractJsonObject(raw).orElseThrow();
        assertThat(cleaned).startsWith("{").doesNotContain("```");
        assertThat(cleaned).contains("\"from\":\"A\"");
        assertThat(HandoffPacketParser.parseObject(raw)).isPresent();
    }

    @Test
    void rejectsNonObject() {
        assertThat(HandoffPacketParser.extractJsonObject("[1,2,3]")).isEmpty();
        assertThat(HandoffPacketParser.extractJsonObject("not json")).isEmpty();
    }

    @Test
    void requireCleanJsonThrowsOnGarbage() {
        assertThatThrownBy(() -> HandoffPacketParser.requireCleanJson("```\nok\n```"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
