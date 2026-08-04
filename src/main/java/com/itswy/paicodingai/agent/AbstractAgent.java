package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.rag.service.KnowledgeService;
import com.itswy.paicodingai.tools.ToolResultHolder;
import com.itswy.paicodingai.vo.ChatEventVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent抽象基类
 *
 * 参考天机学堂实现：
 * - 提供通用的流式对话逻辑
 * - 支持RAG增强（通用功能）
 * - 支持advisors()方法配置额外的Advisor
 */
public abstract class AbstractAgent implements Agent {

    protected final ChatClient chatClient;
    protected final SystemPromptConfig promptConfig;

    /** 知识库服务（RAG）- 可选注入 */
    @Autowired(required = false)
    protected KnowledgeService knowledgeService;

    /** 是否启用RAG（子类可覆盖） */
    protected boolean enableRAG = true;

    /** RAG检索数量（子类可覆盖） */
    protected int ragTopK = 3;

    public AbstractAgent(ChatClient chatClient, SystemPromptConfig promptConfig) {
        this.chatClient = chatClient;
        this.promptConfig = promptConfig;
    }

    /**
     * 获取系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 获取额外的Advisor列表（子类可覆盖）
     *
     * 默认返回空列表，子类可以添加自己的Advisor
     */
    public List<Advisor> extraAdvisors() {
        return List.of();
    }

    @Override
    public Flux<ChatEventVO> chat(String question, AgentContext ctx) {
        return doChat(question, getSystemPrompt(), ctx);
    }

    /**
     * 通用流式对话逻辑（支持RAG）
     *
     * 参考天机学堂实现：
     * - 自动从知识库检索相关文档
     * - 将检索到的文档作为上下文
     * - 支持额外的Advisors
     */
    protected Flux<ChatEventVO> doChat(String question, String systemPrompt, AgentContext ctx) {
        // 1. 构建系统提示词（包含RAG上下文）
        String finalSystemPrompt = buildSystemPromptWithRAG(question, systemPrompt);

        // 2. 构建Advisors列表
        List<Advisor> advisors = extraAdvisors();

        return chatClient.prompt()
            .system(finalSystemPrompt)
            .user(question)
            .advisors(a -> {
                // 添加额外的Advisors
                a.advisors(advisors);
                // 添加记忆管理
                a.param(ChatMemory.CONVERSATION_ID, ctx.getSessionId());
            })
            .toolContext(Map.of("requestId", ctx.getRequestId()))
            .stream()
            .chatResponse()
            .map(response -> {
                var text = response.getResult().getOutput().getText();
                return ChatEventVO.data(text);
            })
            .concatWith(getToolResult(ctx.getRequestId()));
    }

    /**
     * 构建带RAG上下文的系统提示词
     */
    private String buildSystemPromptWithRAG(String question, String systemPrompt) {
        if (!enableRAG || knowledgeService == null) {
            return systemPrompt;
        }

        try {
            // 从知识库检索相关文档
            List<Document> documents = knowledgeService.search(question, ragTopK);

            if (documents.isEmpty()) {
                return systemPrompt;
            }

            // 构建上下文
            String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

            // 将上下文添加到系统提示词
            return systemPrompt + "\n\n参考资料：\n" + context;

        } catch (Exception e) {
            // RAG失败时降级为普通模式
            return systemPrompt;
        }
    }

    /**
     * 获取工具调用结果
     */
    protected Flux<ChatEventVO> getToolResult(String requestId) {
        var result = ToolResultHolder.get(requestId);
        if (result != null && !result.isEmpty()) {
            ToolResultHolder.remove(requestId);
            return Flux.just(ChatEventVO.param(result));
        }
        return Flux.empty();
    }
}
