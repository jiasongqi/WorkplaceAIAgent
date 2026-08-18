package com.yupi.yuaiagent.manifest;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform.manifest")
public class ManifestLoaderProperties {

    private Mode loader = Mode.LEGACY;

    public Mode getLoader() {
        return loader;
    }

    public void setLoader(Mode loader) {
        this.loader = loader == null ? Mode.LEGACY : loader;
    }

    @PostConstruct
    public void validateSupportedMode() {
        if (loader == Mode.UNIFIED) {
            throw new IllegalStateException(
                    "platform.manifest.loader=unified is reserved for a later cutover; use legacy or dual");
        }
    }

    public enum Mode {
        LEGACY,
        DUAL,
        UNIFIED
    }
}
