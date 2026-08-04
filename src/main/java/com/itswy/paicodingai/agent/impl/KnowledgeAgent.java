package com.itswy.paicodingai.agent.impl;

import com.itswy.paicodingai.agent.AbstractAgent;
import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.rag.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库Agent
 *
 * 实现RAG功能：
 * 1. 从VectorStore检索相关文档
 * 2. 将检索到的文档作为上下文
 * 3. 构造Prompt让LLM生成回答
 */
@Slf4j
@Component
public class KnowledgeAgent extends AbstractAgent {

    private final KnowledgeService knowledgeService;

    public KnowledgeAgent(ChatClient chatClient,
                         SystemPromptConfig promptConfig,
                         KnowledgeService knowledgeService) {
        super(chatClient, promptConfig);
        this.knowledgeService = knowledgeService;
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
               "请根据以下参考资料回答问题。\n" +
               "如果资料中没有相关信息，请如实告知。\n" +
               "回答时请直接回答问题，不要说\"根据参考资料\"等词语。\n\n" +
               "参考资料：\n{context}";
    }

    /**
     * 重写chat方法，实现自定义RAG逻辑
     */
    @Override
    public reactor.core.publisher.Flux<com.itswy.paicodingai.vo.ChatEventVO> chat(
            String question,
            com.itswy.paicodingai.agent.AgentContext ctx) {

        log.info("KnowledgeAgent处理问题: {}", question);

        // 1. 从知识库检索相关文档
        List<Document> documents = knowledgeService.search(question, 3);

        // 2. 构造上下文
        String context = documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n---\n"));

        // 3. 构造带上下文的系统提示词
        String systemPrompt = getSystemPrompt().replace("{context}", context);

        // 4. 调用LLM生成回答
        return doChat(question, systemPrompt, ctx);
    }
}
