package com.yupi.yuaiagent.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans for unified RAG pipeline (HyDE + tool registration).
 */
@Configuration
public class RagPipelineConfig {

    @Bean
    HyDERetriever hydeRetriever(ChatModel dashscopeChatModel,
                                @Qualifier("aiChatVectorStore") VectorStore aiChatVectorStore) {
        return new HyDERetriever(dashscopeChatModel, aiChatVectorStore);
    }

    @Bean
    RagTool ragTool(RetrievalPipeline retrievalPipeline, RagRetrievalAttemptTracker attemptTracker) {
        return new RagTool(retrievalPipeline, attemptTracker);
    }
}
