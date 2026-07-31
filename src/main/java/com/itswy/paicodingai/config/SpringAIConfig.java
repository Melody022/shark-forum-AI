package com.itswy.paicodingai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ==========================================================================
 * Spring AI 配置 —— 创建 ChatClient
 * ==========================================================================
 *
 * 第一期：只配 ChatClient + 日志
 * 第二期：加 ChatMemory（对话记忆）
 * 第三期：加 RAG 知识库增强
 *
 * @date 2026-07-18
 */
@Configuration
public class SpringAIConfig {

    /**
     * ChatClient —— AI 对话客户端（核心！）
     *
     * @param chatModel Spring AI 自动注入的大模型
     * @param loggerAdvisor 日志记录器
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, Advisor loggerAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(loggerAdvisor)
                .build();
    }

    /**
     * 日志记录器 Advisor
     */
    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }
}
