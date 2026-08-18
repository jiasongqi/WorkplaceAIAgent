package com.yupi.yuaiagent.diagnostics;

import com.yupi.yuaiagent.permission.PermissionProfileRegistry;
import com.yupi.yuaiagent.registry.AgentRegistry;
import com.yupi.yuaiagent.tools.ToolRegistration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reports configuration drift after the application context is fully initialized.
 *
 * <p>This diagnostic is observation-only. It never changes routing or authorization results.
 */
@Slf4j
@Component
public class PlatformSafetyDiagnostics implements ApplicationRunner {

    private final List<ToolCallback> tools;
    private final AgentRegistry agentRegistry;
    private final PermissionProfileRegistry permissionProfileRegistry;
    private final PlatformSafetyInspector inspector;

    public PlatformSafetyDiagnostics(
            @Qualifier(ToolRegistration.ALL_TOOLS_BEAN_NAME) ToolCallback[] tools,
            AgentRegistry agentRegistry,
            PermissionProfileRegistry permissionProfileRegistry,
            PlatformSafetyInspector inspector
    ) {
        this.tools = tools == null
                ? List.of()
                : Arrays.stream(tools).filter(Objects::nonNull).toList();
        this.agentRegistry = agentRegistry;
        this.permissionProfileRegistry = permissionProfileRegistry;
        this.inspector = inspector;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            inspectAndReport();
        } catch (Exception exception) {
            log.error("[PlatformSafety] inspection failed unexpectedly; application startup continues", exception);
        }
    }

    private void inspectAndReport() {
        Set<String> toolNames = tools.stream()
                .map(ToolCallback::getToolDefinition)
                .filter(Objects::nonNull)
                .map(org.springframework.ai.tool.definition.ToolDefinition::name)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        if (toolNames.isEmpty()) {
            log.warn("[PlatformSafety] no registered tools found; permission-pattern coverage may be incomplete");
        }

        PlatformSafetyReport report = inspector.inspect(
                toolNames,
                permissionProfileRegistry.getAll(),
                agentRegistry.list()
        );

        report.unmatchedPermissionPatterns().forEach((agentCode, patterns) ->
                log.warn("[PlatformSafety] permission patterns match no registered tool: agentCode={}, patterns={}",
                        agentCode, patterns));
        if (!report.agentsWithMissingPermissionProfiles().isEmpty()) {
            log.warn("[PlatformSafety] agents reference missing permission profiles: {}",
                    report.agentsWithMissingPermissionProfiles());
        }
        log.info("[PlatformSafety] inspection complete: tools={}, agents={}, permissionProfiles={}, warnings={}",
                toolNames.size(), agentRegistry.size(), permissionProfileRegistry.size(), report.hasWarnings());
    }
}
