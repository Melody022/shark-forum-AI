package com.itswy.paicodingai.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import com.itswy.paicodingai.memory.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * - 每个会话独立管理记忆
 *
 * 设计原则：
 * - 每个conversationId对应一个独立的RedisConversationMemory实例
 * - 使用ConcurrentHashMap管理多个会话
 * - 支持Token使用情况查询
 */
@Slf4j
@Component
public class TokenAwareChatMemory implements ChatMemory {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final TokenBudget tokenBudget;

    /** 存储每个会话的短期记忆实例 */
    private final ConcurrentHashMap<String, RedisConversationMemory> memoryMap = new ConcurrentHashMap<>();

    public TokenAwareChatMemory(RedisUtils redisUtils, ObjectMapper objectMapper, TokenBudget tokenBudget) {
        this.redisUtils = redisUtils;
        this.objectMapper = objectMapper;
        this.tokenBudget = tokenBudget;
    }

    /**
     * 获取或创建会话的短期记忆
     *
     * @param conversationId 会话ID
     * @return RedisConversationMemory实例
     */
    public RedisConversationMemory getOrCreateMemory(String conversationId) {
        return memoryMap.computeIfAbsent(conversationId, id -> {
            log.debug("创建新的会话记忆: conversationId={}", id);
            return new RedisConversationMemory(
                    redisUtils,
                    objectMapper,
                    id,
                    tokenBudget.getAvailableForConversation()
            );
        });
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

        RedisConversationMemory memory = getOrCreateMemory(conversationId);

        for (Message message : messages) {
            // 转换为MemoryEntry
            MemoryEntry entry = convertToMemoryEntry(message);

            // 存入短期记忆（会自动检查Token预算并淘汰）
            memory.store(entry);

            log.debug("添加消息到记忆: conversationId={}, type={}, tokens={}",
                    conversationId, message.getMessageType(), entry.getTokenCount());
        }

        // 检查是否需要压缩
        checkAndCompress(conversationId, memory);
    }

    /**
     * 获取会话的所有消息
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    @Override
    public List<Message> get(String conversationId) {
        RedisConversationMemory memory = getOrCreateMemory(conversationId);

        // 获取所有MemoryEntry
        List<MemoryEntry> allEntries = memory.getAll();

        // 转换为Message列表
        List<Message> messages = new ArrayList<>();
        for (MemoryEntry entry : allEntries) {
            Message message = convertToMessage(entry);
            if (message != null) {
                messages.add(message);
            }
        }

        log.debug("获取会话记忆: conversationId={}, 消息数={}, tokens={}",
                conversationId, messages.size(), memory.getTokenCount());

        return messages;
    }

    /**
     * 清空会话记忆
     *
     * @param conversationId 会话ID
     */
    @Override
    public void clear(String conversationId) {
        RedisConversationMemory memory = memoryMap.remove(conversationId);
        if (memory != null) {
            memory.clear();
            log.info("清空会话记忆: conversationId={}", conversationId);
        }
    }

    /**
     * 检查并执行压缩
     */
    private void checkAndCompress(String conversationId, RedisConversationMemory memory) {
        // 检查Token是否超过预算的90%
        if (!tokenBudget.needsCompression(memory)) {
            return;
        }

        log.info("Token占用超过阈值，触发压缩: conversationId={}, 当前={}, 预算={}",
                conversationId, memory.getTokenCount(), tokenBudget.getAvailableForConversation());

        // 执行压缩
        int beforeTokens = memory.getTokenCount();

        // TODO: 调用ContextCompressor进行压缩
        // ContextCompressor compressor = new ContextCompressor(chatService);
        // compressor.compress(memory);

        int afterTokens = memory.getTokenCount();
        log.info("压缩完成: {} → {} tokens", beforeTokens, afterTokens);
    }

    /**
     * 将Message转换为MemoryEntry
     */
    private MemoryEntry convertToMemoryEntry(Message message) {
        String source = message.getMessageType() != null ? message.getMessageType().name() : "UNKNOWN";

        return new MemoryEntry(
                UUID.randomUUID().toString().substring(0, 8),
                message.getText(),
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", source),
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

    // ==================== Token使用情况查询 ====================

    /**
     * 获取指定会话的Token使用情况
     *
     * @param conversationId 会话ID
     * @return Token使用情况
     */
    public TokenUsageInfo getTokenUsage(String conversationId) {
        RedisConversationMemory memory = getOrCreateMemory(conversationId);

        return new TokenUsageInfo(
                conversationId,
                memory.size(),
                memory.getTokenCount(),
                memory.getMaxTokens(),
                memory.getUsageRatio()
        );
    }

    /**
     * 获取所有会话的Token使用情况
     *
     * @return 所有会话的Token使用情况列表
     */
    public List<TokenUsageInfo> getAllTokenUsage() {
        List<TokenUsageInfo> usageList = new ArrayList<>();

        for (Map.Entry<String, RedisConversationMemory> entry : memoryMap.entrySet()) {
            RedisConversationMemory memory = entry.getValue();
            usageList.add(new TokenUsageInfo(
                    entry.getKey(),
                    memory.size(),
                    memory.getTokenCount(),
                    memory.getMaxTokens(),
                    memory.getUsageRatio()
            ));
        }

        return usageList;
    }

    /**
     * 获取记忆状态
     */
    public String getStatus() {
        return String.format(
                "TokenAwareChatMemory: %d个会话, 总消息数=%d, 总tokens=%d",
                memoryMap.size(),
                memoryMap.values().stream().mapToInt(RedisConversationMemory::size).sum(),
                memoryMap.values().stream().mapToInt(RedisConversationMemory::getTokenCount).sum()
        );
    }

    // ==================== 内部数据类 ====================

    /**
     * Token使用情况信息
     */
    public record TokenUsageInfo(
            String conversationId,
            int messageCount,
            int currentTokens,
            int maxTokens,
            double usageRatio
    ) {
        /**
         * 获取格式化的使用率
         */
        public String getFormattedUsageRatio() {
            return String.format("%.1f%%", usageRatio * 100);
        }

        /**
         * 获取剩余可用Token数
         */
        public int getRemainingTokens() {
            return maxTokens - currentTokens;
        }

        /**
         * 获取格式化的Token数
         */
        public String getFormattedTokens(int tokens) {
            if (tokens >= 1000) {
                return String.format("%.1fk", tokens / 1000.0);
            }
            return String.valueOf(tokens);
        }
    }
}
