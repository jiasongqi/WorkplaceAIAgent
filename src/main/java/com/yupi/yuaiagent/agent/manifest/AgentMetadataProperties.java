package com.yupi.yuaiagent.agent.manifest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform.agent")
public class AgentMetadataProperties {

    private Source metadataSource = Source.LEGACY;

    public Source getMetadataSource() {
        return metadataSource;
    }

    public void setMetadataSource(Source metadataSource) {
        this.metadataSource = metadataSource == null ? Source.LEGACY : metadataSource;
    }

    public enum Source {
        LEGACY,
        SHADOW,
        REGISTRY
    }
}
