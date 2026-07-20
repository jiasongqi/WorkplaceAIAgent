package com.yupi.yuaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 创建上下文查询增强器的工厂：当知识库检索不到匹配文档时，明确告知用户"未检索到相关内容"，
 * 而不是暗示答案来自知识库；随后仍允许模型基于通用经验作答（allowEmptyContext=true）。
 */
public class AiChatContextualQueryAugmenterFactory {

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                知识库未检索到相关内容，将基于通用经验回答，请注意非公司内部政策，请以此作为你回答用户问题前的说明。
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
