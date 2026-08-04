package com.itswy.paicodingai.agent.impl;

import com.itswy.paicodingai.agent.AbstractAgent;
import com.itswy.paicodingai.config.SystemPromptConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 知识库Agent
 *
 * 处理知识库问答，使用通用RAG功能
 * 参考天机学堂实现：RAG是通用功能，所有Agent都可以使用
 */
@Slf4j
@Component
public class KnowledgeAgent extends AbstractAgent {

    public KnowledgeAgent(ChatClient chatClient,
                         SystemPromptConfig promptConfig) {
        super(chatClient, promptConfig);
        // 启用RAG，使用更多检索结果
        this.enableRAG = true;
        this.ragTopK = 5;
    }

    @Override
    public String getName() {
        return "KnowledgeAgent";
    }

    @Override
    public String getDescription() {
        return "知识库检索和问答，处理技术问题、知识性问题";
    }

    @Override
    protected String getSystemPrompt() {
        return "你是知识库助手，专门从知识库中检索信息并回答用户问题。\n" +
               "请根据参考资料回答问题。\n" +
               "如果资料中没有相关信息，请如实告知。\n" +
               "回答时请直接回答问题，不要说\"根据参考资料\"等词语。";
    }
}
