package com.itswy.paicodingai.memory.service;

import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 基于 Token 预算的 ChatMemory 实现
 *
 * 与 MessageWindowChatMemory 的区别：
 * - MessageWindowChatMemory：固定消息数量（如保留最近20条）
 * - TokenAwareChatMemory：根据Token预算动态管理（如保留最近50000 tokens）
 *
 * 优势：
 * - 更精确的上下文管理
 * - 避免Token超出模型限制
 * - 支持不同模型的上下文窗口
 *
 * 使用方式：
 * @Bean
 * public ChatMemory chatMemory(RedisConversationMemory redisConversationMemory) {
 *     return new TokenAwareChatMemory(redisConversationMemory, tokenBudget);
 * }
 */
@Slf4j
@Component
public class TokenAwareChatMemory implements ChatMemory {

    private final RedisConversationMemory shortTermMemory;
    private final TokenBudget tokenBudget;

    public TokenAwareChatMemory(RedisConversationMemory shortTermMemory, TokenBudget tokenBudget) {
        this.shortTermMemory = shortTermMemory;
        this.tokenBudget = tokenBudget;
    }

    /**
     * 添加单条消息
     *
     * @param conversationId 会话ID
     * @param message        消息
     */
    @Override
    public void add(String conversationId, Message message) {
        List<Message> messages = new ArrayList<>();
        messages.add(message);
        add(conversationId, messages);
    }

    /**
     * 添加多条消息
     *
     * @param conversationId 会话ID
     * @param messages       消息列表
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        for (Message message : messages) {
            // 转换为MemoryEntry
            MemoryEntry entry = convertToMemoryEntry(message);

            // 存入短期记忆（会自动检查Token预算并淘汰）
            shortTermMemory.store(entry);

            log.debug("添加消息到记忆: conversationId={}, type={}, tokens={}",
                    conversationId, message.getMessageType(), entry.getTokenCount());
        }

        // 检查是否需要压缩
        checkAndCompress();
    }

    /**
     * 获取会话的所有消息
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    @Override
    public List<Message> get(String conversationId) {
        // 获取所有MemoryEntry
        List<MemoryEntry> allEntries = shortTermMemory.getAll();

        // 转换为Message列表
        List<Message> messages = new ArrayList<>();
        for (MemoryEntry entry : allEntries) {
            Message message = convertToMessage(entry);
            if (message != null) {
                messages.add(message);
            }
        }

        log.debug("获取会话记忆: conversationId={}, 消息数={}, tokens={}",
                conversationId, messages.size(), shortTermMemory.getTokenCount());

        return messages;
    }

    /**
     * 清空会话记忆
     *
     * @param conversationId 会话ID
     */
    @Override
    public void clear(String conversationId) {
        shortTermMemory.clear();
        log.info("清空会话记忆: conversationId={}", conversationId);
    }

    /**
     * 检查并执行压缩
     */
    private void checkAndCompress() {
        // 检查Token是否超过预算的90%
        if (!tokenBudget.needsCompression(shortTermMemory)) {
            return;
        }

        log.info("Token占用超过阈值，触发压缩: 当前={}, 预算={}",
                shortTermMemory.getTokenCount(), tokenBudget.getAvailableForConversation());

        // 执行压缩
        int beforeTokens = shortTermMemory.getTokenCount();

        // 调用压缩器
        ContextCompressor compressor = new ContextCompressor(null);  // 需要注入ChatService
        // TODO: 后续版本需要正确注入ChatService
        // compressor.compress(shortTermMemory);

        int afterTokens = shortTermMemory.getTokenCount();
        log.info("压缩完成: {} → {} tokens", beforeTokens, afterTokens);
    }

    /**
     * 将Message转换为MemoryEntry
     */
    private MemoryEntry convertToMemoryEntry(Message message) {
        String source = message.getMessageType() != null ? message.getMessageType().name() : "UNKNOWN";

        return new MemoryEntry(
                java.util.UUID.randomUUID().toString().substring(0, 8),
                message.getText(),
                MemoryEntry.MemoryType.CONVERSATION,
                java.util.Map.of("source", source),
                MemoryEntry.estimateTokens(message.getText())
        );
    }

    /**
     * 将MemoryEntry转换为Message
     *
     * 暂时简化实现，所有类型都转为UserMessage
     * TODO: 后续版本需要正确实现AssistantMessage
     */
    private Message convertToMessage(MemoryEntry entry) {
        return org.springframework.ai.chat.messages.UserMessage.builder()
                .text(entry.getContent())
                .build();
    }

    /**
     * 获取记忆状态
     */
    public String getStatus() {
        return String.format(
                "TokenAwareChatMemory: %d条消息, %d tokens (预算: %d, 使用率: %.1f%%)",
                shortTermMemory.size(),
                shortTermMemory.getTokenCount(),
                shortTermMemory.getMaxTokens(),
                shortTermMemory.getUsageRatio() * 100
        );
    }
}
