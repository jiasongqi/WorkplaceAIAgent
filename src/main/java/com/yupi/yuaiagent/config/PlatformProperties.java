package com.yupi.yuaiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Plugin-platform feature flags. Defaults preserve legacy behavior.
 */
@Component
@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private Agent agent = new Agent();
    private Permission permission = new Permission();
    private RuntimeTools runtimeTools = new RuntimeTools();
    private Transformer toolTransformer = new Transformer();
    private PromptContributors promptContributors = new PromptContributors();
    private ActivationCache activationCache = new ActivationCache();
    private Pack pack = new Pack();

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent == null ? new Agent() : agent;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission == null ? new Permission() : permission;
    }

    public RuntimeTools getRuntimeTools() {
        return runtimeTools;
    }

    public void setRuntimeTools(RuntimeTools runtimeTools) {
        this.runtimeTools = runtimeTools == null ? new RuntimeTools() : runtimeTools;
    }

    public Transformer getToolTransformer() {
        return toolTransformer;
    }

    public void setToolTransformer(Transformer toolTransformer) {
        this.toolTransformer = toolTransformer == null ? new Transformer() : toolTransformer;
    }

    public PromptContributors getPromptContributors() {
        return promptContributors;
    }

    public void setPromptContributors(PromptContributors promptContributors) {
        this.promptContributors = promptContributors == null ? new PromptContributors() : promptContributors;
    }

    public ActivationCache getActivationCache() {
        return activationCache;
    }

    public void setActivationCache(ActivationCache activationCache) {
        this.activationCache = activationCache == null ? new ActivationCache() : activationCache;
    }

    public Pack getPack() {
        return pack;
    }

    public void setPack(Pack pack) {
        this.pack = pack == null ? new Pack() : pack;
    }

    public boolean agentRunnerEnabled() {
        return agent.getRunner().isEnabled();
    }

    public String agentRunnerRoute() {
        return agent.getRunner().getRoute();
    }

    public Map<String, String> agentRunnerIntents() {
        return agent.getRunner().getIntents();
    }

    public static class Agent {
        private Runner runner = new Runner();

        public Runner getRunner() {
            return runner;
        }

        public void setRunner(Runner runner) {
            this.runner = runner == null ? new Runner() : runner;
        }
    }

    public static class Runner {
        private boolean enabled = false;
        private String route = "off";
        private Map<String, String> intents = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route == null ? "off" : route;
        }

        public Map<String, String> getIntents() {
            return intents;
        }

        public void setIntents(Map<String, String> intents) {
            this.intents = intents == null ? new HashMap<>() : intents;
        }
    }

    public static class Permission {
        private String namespaceMode = "off";
        private boolean packNarrowing = false;

        public String getNamespaceMode() {
            return namespaceMode;
        }

        public void setNamespaceMode(String namespaceMode) {
            this.namespaceMode = namespaceMode == null ? "off" : namespaceMode;
        }

        public boolean isPackNarrowing() {
            return packNarrowing;
        }

        public void setPackNarrowing(boolean packNarrowing) {
            this.packNarrowing = packNarrowing;
        }
    }

    public static class RuntimeTools {
        private boolean requestFilter = false;

        public boolean isRequestFilter() {
            return requestFilter;
        }

        public void setRequestFilter(boolean requestFilter) {
            this.requestFilter = requestFilter;
        }
    }

    public static class Transformer {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PromptContributors {
        private String mode = "legacy";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode == null ? "legacy" : mode;
        }
    }

    public static class ActivationCache {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Pack {
        /** file | jdbc */
        private String storage = "file";

        public String getStorage() {
            return storage;
        }

        public void setStorage(String storage) {
            this.storage = storage == null ? "file" : storage;
        }
    }
}
