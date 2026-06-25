package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.sandbox.SandboxFactory;
import com.yupi.yuaiagent.sandbox.SandboxPolicy;
import com.yupi.yuaiagent.sandbox.SandboxResult;
import com.yupi.yuaiagent.sandbox.ToolSandbox;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerminalOperationToolTest {

    @Test
    void executeTerminalCommand() {
        // Mock SandboxFactory to return a mock sandbox
        SandboxFactory sandboxFactory = mock(SandboxFactory.class);
        ToolSandbox sandbox = mock(ToolSandbox.class);
        when(sandboxFactory.getSandbox()).thenReturn(sandbox);

        // Mock sandbox execute to return a successful result
        SandboxResult mockResult = SandboxResult.builder()
                .exitCode(0)
                .stdout("mock output")
                .stderr("")
                .build();
        when(sandbox.execute(any())).thenReturn(mockResult);
        when(sandbox.getPolicy()).thenReturn(SandboxPolicy.PROCESS_SANDBOX);

        TerminalOperationTool terminalOperationTool = new TerminalOperationTool(sandboxFactory);
        String command = "dir";
        String result = terminalOperationTool.executeTerminalCommand(command);
        Assertions.assertNotNull(result);
    }
}
