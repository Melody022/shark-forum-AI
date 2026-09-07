package com.itswy.paicodingai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 三层意图分类专用 ChatClient 装配。
 *
 * <p>为避免意图分类被全局 {@code ChatClient} 的会话记忆 Advisor 污染(旧的实现把分类结果
 * 写进固定 conversationId=intent-classify 的 Redis 记忆,导致前序输入影响后续分类),
 * 这里按用途构建「无记忆、无工具」的独立 ChatClient:
 * <ul>
 *   <li>smallIntentChatClient —— 第 2 层小模型(默认 Ollama),每次分类独立调用;</li>
 *   <li>largeIntentChatClient —— 第 3 层大模型兜底,包装激活 provider(DynamicChatModel)。</li>
 * </ul>
 * 底层 ChatModel 由 {@link DynamicChatModel#getChatModel} 按 (baseUrl, model, apiKey) 缓存复用。
 */
@Slf4j
@Component
public class IntentModelConfig {

    private final ChatClient smallIntentChatClient;
    private final ChatClient largeIntentChatClient;

    public IntentModelConfig(DynamicChatModel dynamicChatModel, IntentRoutingProperties properties) {
        IntentRoutingProperties.SmallModel small = properties.getIntentSmall();
        ChatModel smallChatModel = dynamicChatModel.getChatModel(small.getBaseUrl(), small.getModel(), small.getApiKey());
        this.smallIntentChatClient = ChatClient.builder(smallChatModel).build();
        this.largeIntentChatClient = ChatClient.builder(dynamicChatModel).build();
        log.info("意图小模型初始化: baseUrl={}, model={}", small.getBaseUrl(), small.getModel());
    }

    public ChatClient smallIntentChatClient() {
        return smallIntentChatClient;
    }

    public ChatClient largeIntentChatClient() {
        return largeIntentChatClient;
    }
}
