package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.tools.ToolResultHolder;
import com.itswy.paicodingai.vo.ChatEventVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Agent抽象基类
 *
 * 参考天机学堂实现：
 * - 提供通用的流式对话逻辑
 * - 支持advisors()方法配置RAG等增强功能
 * - 每个Agent可以覆盖advisors()返回自己的Advisor列表
 */
public abstract class AbstractAgent implements Agent {

    protected final ChatClient chatClient;
    protected final SystemPromptConfig promptConfig;

    public AbstractAgent(ChatClient chatClient, SystemPromptConfig promptConfig) {
        this.chatClient = chatClient;
        this.promptConfig = promptConfig;
    }

    /**
     * 获取系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 获取Advisor列表（子类可覆盖）
     *
     * 默认返回空列表，需要RAG的Agent覆盖此方法
     * 例如：KnowledgeAgent覆盖此方法返回QuestionAnswerAdvisor
     */
    public List<Advisor> advisors() {
        return List.of();
    }

    @Override
    public Flux<ChatEventVO> chat(String question, AgentContext ctx) {
        return doChat(question, getSystemPrompt(), ctx);
    }

    /**
     * 通用流式对话逻辑
     *
     * 参考天机学堂实现：
     * - 使用advisors()方法添加RAG等增强
     * - 自动处理记忆管理
     * - 自动处理工具调用结果
     */
    protected Flux<ChatEventVO> doChat(String question, String systemPrompt, AgentContext ctx) {
        return chatClient.prompt()
            .system(systemPrompt)
            .user(question)
            .advisors(a -> {
                // 添加Agent特有的Advisors（如RAG）
                a.advisors(this.advisors());
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
