package com.itswy.paicodingai.agent.impl;

import com.itswy.paicodingai.agent.AbstractAgent;
import com.itswy.paicodingai.config.SystemPromptConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 通用Agent
 *
 * 处理通用对话（问候、闲聊等）
 */
@Component
public class GeneralAgent extends AbstractAgent {

    public GeneralAgent(ChatClient chatClient,
                       SystemPromptConfig promptConfig) {
        super(chatClient, promptConfig);
    }

    @Override
    public String getName() {
        return "GeneralAgent";
    }

    @Override
    public String getDescription() {
        return "处理通用对话";
    }

    @Override
    protected String getSystemPrompt() {
        return "你是一个友好的AI助手，可以进行日常对话、回答一般性问题。\n" +
               "请用友好、专业的语气回答用户问题。";
    }
}
