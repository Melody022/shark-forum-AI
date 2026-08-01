package com.itswy.paicodingai.config;

import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import com.itswy.paicodingai.memory.service.TokenAwareChatMemory;
import com.itswy.paicodingai.memory.service.TokenBudget;
import com.itswy.paicodingai.memory.util.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ==========================================================================
 * Spring AI 配置 —— 创建 ChatClient
 * ==========================================================================
 *
 * 第一期：只配 ChatClient + 日志
 * 第二期：加 ChatMemory（对话记忆） ✅ 已实现
 * 第三期：加 RAG 知识库增强
 *
 * 设计决策：
 * - 不使用 MessageWindowChatMemory（固定消息数）
 * - 使用 TokenAwareChatMemory（基于Token预算）
 * - 支持不同模型的上下文窗口
 *
 * @date 2026-07-31
 */
@Configuration
public class SpringAIConfig {

    /** 记忆窗口大小（用于兼容旧代码，实际使用Token预算） */
    private static final int MEMORY_WINDOW_SIZE = 20;

    /**
     * ChatClient —— AI 对话客户端（核心！）
     *
     * @param chatModel Spring AI 自动注入的大模型
     * @param loggerAdvisor 日志记录器
     * @param memoryAdvisor 对话记忆顾问
     */
    @Bean
    public ChatClient chatClient(
            ChatModel chatModel,
            Advisor loggerAdvisor,
            Advisor memoryAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(loggerAdvisor)
                .defaultAdvisors(memoryAdvisor)  // 添加记忆顾问
                .build();
    }

    /**
     * 日志记录器 Advisor
     */
    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    /**
     * Token 预算管理器
     *
     * 支持不同模型的上下文窗口：
     * - DeepSeek: 1,048,576 tokens (1M)
     - GPT-4: 128,000 tokens (128k)
     * - Claude: 200,000 tokens (200k)
     */
    @Bean
    public TokenBudget tokenBudget() {
        return new TokenBudget();
    }

    /**
     * Token 感知的 ChatMemory
     *
     * 替代 MessageWindowChatMemory，优势：
     * - 根据 Token 预算动态管理
     * - 避免固定消息数的问题
     * - 更精确的上下文管理
     * - 每个会话独立管理记忆
     */
    @Bean
    public ChatMemory chatMemory(
            RedisUtils redisUtils,
            ObjectMapper objectMapper,
            TokenBudget tokenBudget) {
        return new TokenAwareChatMemory(redisUtils, objectMapper, tokenBudget);
    }

    /**
     * 对话记忆 Advisor
     *
     * 使用 TokenAwareChatMemory（基于Token预算）
     * 每次请求时自动加载历史消息并注入到 system prompt 中
     */
    @Bean
    public Advisor memoryAdvisor(ChatMemory chatMemory) {
        return org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
