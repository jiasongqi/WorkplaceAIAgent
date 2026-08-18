package com.yupi.yuaiagent.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolAuthorizationCoverageTest {

    @Test
    void mcpChecksStayOnAccessDecisionContext() {
        AccessDecisionContext ctx = AccessDecisionContext.builder()
                .agentCode("resume-agent")
                .toolName("searchWeb")
                .mcpServerId("yu-image-search-mcp-server")
                .userId("u1")
                .build();
        assertThat(ctx.getMcpServerId()).isNotBlank();
        assertThat(ctx.getUserId()).isEqualTo("u1");
    }
}
