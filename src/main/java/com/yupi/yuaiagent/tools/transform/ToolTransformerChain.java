package com.yupi.yuaiagent.tools.transform;

import java.util.List;

public class ToolTransformerChain implements ToolTransformer {

    private final List<ToolTransformer> transformers;

    public ToolTransformerChain(List<ToolTransformer> transformers) {
        this.transformers = transformers == null ? List.of() : List.copyOf(transformers);
    }

    @Override
    public TransformResult transform(String toolName, String input) {
        String current = input;
        try {
            for (ToolTransformer transformer : transformers) {
                TransformResult result = transformer.transform(toolName, current);
                if (result == null || result.decision() == TransformDecision.REJECT) {
                    return TransformResult.reject(result == null ? "null transformer result" : result.reason());
                }
                if (result.decision() == TransformDecision.REWRITE) {
                    current = result.payload();
                }
            }
            return TransformResult.proceed(current);
        } catch (RuntimeException ex) {
            return TransformResult.reject("transformer failed: " + ex.getMessage());
        }
    }
}
