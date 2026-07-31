package com.itswy.paicodingai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * ==========================================================================
 * 系统提示词配置 —— AI的角色和行为定义
 * ==========================================================================
 *
 * 什么是 system message（系统提示词）？
 *   就是在对话开始前，先告诉 AI "你是谁、要干什么"。
 *   比如："你是技术派论坛的AI助手，回答问题要简洁，用中文。"
 *
 * AI 收到的完整 prompt 实际上是：
 *   system message + 历史消息 + 用户当前问题
 *   AI 根据 system message 来决定回答风格和范围
 *
 * @date 2026-07-18
 */
@Configuration
public class SystemPromptConfig {

    /**
     * 聊天系统提示词
     *
     * @Value 的格式：${配置key:默认值}
     * 冒号后面是默认值，如果配置文件没配就用默认值
     *
     * 配置项：ai.system.chat
     * application.yml 中已配好
     */
    @Value("${ai.system.chat:你是技术派论坛的AI助手，请用中文简洁地回答问题。}")
    private String chatSystemMessage;

    public String getChatSystemMessage() {
        return chatSystemMessage;
    }
}
