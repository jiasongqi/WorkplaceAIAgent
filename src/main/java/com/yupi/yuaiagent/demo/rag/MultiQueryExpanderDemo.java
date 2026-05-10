package com.yupi.yuaiagent.demo.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询扩展器 Demo，程序员优化效率的关键，3-5个提高查询的效率，查询扩展可以用便宜的模型，节省成本
 * 步骤如下：
 * 1、使用扩展后的查询召回文档：遍历扩展后的查询列表，对每个查询进行文档检索（DocumentRetriever），并返回结果
 * 2、整合召回的文档：将每个查询召回的文档进行整合，形成一个保安所有相关信息文档集合，使用文档合并器去重
 * 3、使用召回文档改写prompt，整合后添加到原始的 prompt中，为LLM提供上下文信息
 * @author jsq
 */
@Component
public class MultiQueryExpanderDemo {

    private final ChatClient.Builder chatClientBuilder;

    public MultiQueryExpanderDemo(ChatModel dashscopeChatModel) {
        this.chatClientBuilder = ChatClient.builder(dashscopeChatModel);
    }

    public List<Query> expand(String query) {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(3)
                .build();
        List<Query> queries = queryExpander.expand(new Query(query));
        return queries;
    }
}
