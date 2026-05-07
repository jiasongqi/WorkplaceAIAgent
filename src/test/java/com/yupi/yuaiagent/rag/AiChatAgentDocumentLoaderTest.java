package com.yupi.yuaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiChatAgentDocumentLoaderTest {

    @Resource
    private AiChatDocumentLoader aiChatDocumentLoader;

    @Test
    void loadMarkdowns() {
        aiChatDocumentLoader.loadMarkdowns();
    }
}