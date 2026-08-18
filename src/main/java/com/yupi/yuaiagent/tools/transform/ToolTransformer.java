package com.yupi.yuaiagent.tools.transform;

@FunctionalInterface
public interface ToolTransformer {
    TransformResult transform(String toolName, String input);
}
