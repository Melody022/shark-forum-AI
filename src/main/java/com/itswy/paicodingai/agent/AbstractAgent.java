package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.knowledge.service.SearchResult;
import com.itswy.paicodingai.knowledge.service.VectorSearchService;
import com.itswy.paicodingai.rag.service.KnowledgeService;
import com.itswy.paicodingai.skill.Skill;
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
 * - 支持Skill增强（技能上下文注入）
 * - 支持advisors()方法配置额外的Advisor
 */
public abstract class AbstractAgent implements Agent {

    protected final ChatClient chatClient;
    protected final SystemPromptConfig promptConfig;

    /** 知识库服务（RAG）- 可选注入 */
    @Autowired(required = false)
    protected KnowledgeService knowledgeService;

    /** 持久化知识库检索服务。 */
    @Autowired(required = false)
    protected VectorSearchService vectorSearchService;

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
        return doChat(question, getSystemPrompt(), ctx, null);
    }

    @Override
    public Flux<ChatEventVO> chat(String question, AgentContext ctx, Skill skill) {
        return doChat(question, getSystemPrompt(), ctx, skill);
    }

    /**
     * 通用流式对话逻辑（支持RAG和Skill）
     *
     * 参考天机学堂实现：
     * - 自动从知识库检索相关文档
     * - 将检索到的文档作为上下文
     * - 将Skill的body注入到系统提示词
     * - 支持额外的Advisors
     */
    protected Flux<ChatEventVO> doChat(String question, String systemPrompt, AgentContext ctx, Skill skill) {
        // 1. 构建系统提示词（包含RAG上下文和Skill）
        String finalSystemPrompt = buildSystemPromptWithRAGAndSkill(question, systemPrompt, skill, ctx);

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
     * 构建带RAG上下文和Skill的系统提示词
     */
    private String buildSystemPromptWithRAGAndSkill(String question, String systemPrompt,
                                                    Skill skill, AgentContext ctx) {
        String finalPrompt = systemPrompt;

        // 1. 添加Skill上下文（如果有）
        if (skill != null && skill.getBody() != null && !skill.getBody().isBlank()) {
            finalPrompt = finalPrompt + "\n\n## 技能指南\n" + skill.getBody();
        }

        return appendRagContext(finalPrompt, question, ctx);
    }

    /** 为子 Agent 提供统一的知识库上下文构建逻辑。 */
    protected String appendRagContext(String prompt, String question, AgentContext ctx) {
        if (!enableRAG) {
            return prompt;
        }
        try {
            if (vectorSearchService != null && ctx != null) {
                String userId = ctx.getUserId() == null || ctx.getUserId().isBlank() ? "0" : ctx.getUserId();
                List<SearchResult> results = vectorSearchService.search(question, userId, ragTopK);
                if (!results.isEmpty()) {
                    String context = results.stream()
                            .map(this::formatSearchResult)
                            .collect(Collectors.joining("\n---\n"));
                    return prompt + "\n\n## 知识库参考资料\n" + context
                            + "\n\n请仅依据参考资料回答相关问题，并在回答中保留文件、章节或页码来源。";
                }
            }

            // 兼容旧的内存知识库演示功能。
            if (knowledgeService != null) {
                List<Document> documents = knowledgeService.search(question, ragTopK);
                if (!documents.isEmpty()) {
                    return prompt + "\n\n参考资料：\n" + documents.stream()
                            .map(Document::getText)
                            .collect(Collectors.joining("\n---\n"));
                }
            }
        } catch (Exception ignored) {
            // 检索失败时继续普通对话，避免知识库故障阻塞聊天。
        }
        return prompt;
    }

    private String formatSearchResult(SearchResult result) {
        StringBuilder source = new StringBuilder("来源：");
        if (result.fileName != null && !result.fileName.isBlank()) {
            source.append(result.fileName);
        }
        if (result.sectionPath != null && !result.sectionPath.isBlank()) {
            source.append(" / ").append(result.sectionPath);
        }
        if (result.pageStart != null && result.pageStart > 0) {
            source.append(" / 第").append(result.pageStart);
            if (result.pageEnd != null && !result.pageEnd.equals(result.pageStart)) {
                source.append('-').append(result.pageEnd);
            }
            source.append("页");
        }
        return source + "\n" + (result.context == null || result.context.isBlank()
                ? result.content : result.context);
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
