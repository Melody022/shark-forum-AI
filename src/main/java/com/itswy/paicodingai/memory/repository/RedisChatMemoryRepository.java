package com.itswy.paicodingai.memory.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于 Redis 的 ChatMemoryRepository 实现
 *
 * 适配 Spring AI 2.0 的 ChatMemoryRepository 接口
 * 使用 RedisUtils 统一管理 Redis 操作
 *
 * 功能：
 * - 保存所有消息（saveAll）
 * - 根据会话 ID 查找消息（findByConversationId）
 * - 根据会话 ID 删除消息（deleteByConversationId）
 *
 * 存储结构：
 * Key: chat:memory:{conversationId}
 * Value: [MemoryEntry1.json, MemoryEntry2.json, ...]
 * TTL: 7天
 *
 * 设计原则：
 * 1. 通过 RedisUtils 统一管理 Redis 操作
 * 2. 键名由 RedisUtils 统一生成
 * 3. 便于后期切换存储方案
 */
@Slf4j
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryRepository(RedisUtils redisUtils, ObjectMapper objectMapper) {
        this.redisUtils = redisUtils;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存所有消息
     *
     * @param conversationId 会话 ID
     * @param messages       消息列表
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            // 转换为 MemoryEntry 并存储
            List<String> jsonList = new ArrayList<>();
            for (Message message : messages) {
                MemoryEntry entry = convertToMemoryEntry(message);
                String json = objectMapper.writeValueAsString(entry);
                jsonList.add(json);
            }

            // 通过 RedisUtils 批量存储
            redisUtils.saveAllMemory(conversationId, jsonList);

            log.debug("保存 {} 条消息到 Redis: conversationId={}", messages.size(), conversationId);

        } catch (Exception e) {
            log.error("保存消息到 Redis 失败: conversationId={}", conversationId, e);
            throw new RuntimeException("保存对话记忆失败", e);
        }
    }

    /**
     * 根据会话 ID 查找所有消息
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            // 通过 RedisUtils 获取所有消息
            List<String> jsonList = redisUtils.getAllMemory(conversationId);

            if (jsonList.isEmpty()) {
                return new ArrayList<>();
            }

            // 反序列化为 Message 对象
            List<Message> messages = new ArrayList<>();
            for (String json : jsonList) {
                MemoryEntry entry = objectMapper.readValue(json, MemoryEntry.class);
                Message message = convertToMessage(entry);
                messages.add(message);
            }

            log.debug("从 Redis 读取 {} 条消息: conversationId={}", messages.size(), conversationId);

            return messages;

        } catch (Exception e) {
            log.error("从 Redis 读取消息失败: conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据会话 ID 删除所有消息
     *
     * @param conversationId 会话 ID
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            redisUtils.clearMemory(conversationId);
            log.debug("删除 Redis 中的对话记忆: conversationId={}", conversationId);

        } catch (Exception e) {
            log.error("删除 Redis 对话记忆失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 查找所有会话 ID
     *
     * @return 会话 ID 列表
     */
    @Override
    public List<String> findConversationIds() {
        try {
            // 获取所有chat:memory:*的键
            Set<String> keys = redisUtils.keys(redisUtils.memoryKey("*"));

            // 提取conversationId
            return keys.stream()
                    .map(redisUtils::extractConversationId)
                    .filter(id -> id != null && !id.isEmpty())
                    .toList();

        } catch (Exception e) {
            log.error("查找所有会话ID失败", e);
            return List.of();
        }
    }

    /**
     * 将 Message 转换为 MemoryEntry
     */
    private MemoryEntry convertToMemoryEntry(Message message) {
        return new MemoryEntry(
                UUID.randomUUID().toString().substring(0, 8),
                message.getText(),
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", message.getMessageType() != null ? message.getMessageType().name() : "UNKNOWN"),
                MemoryEntry.estimateTokens(message.getText())
        );
    }

    /**
     * 将 MemoryEntry 转换为 Message
     *
     * 根据 MemoryType 返回不同的 Message 实现：
     * - CONVERSATION + source=USER → UserMessage
     * - CONVERSATION + source=ASSISTANT → AssistantMessage
     * - SUMMARY → UserMessage（摘要视为用户消息）
     * - TOOL_RESULT → UserMessage（工具结果暂存为用户消息）
     * - FACT → UserMessage（事实视为用户消息）
     */
    private Message convertToMessage(MemoryEntry entry) {
        // 获取消息来源
        String source = entry.getMetadata().get("source");

        // 暂时简化实现：所有类型都转为UserMessage
        // TODO: 后续版本需要正确实现AssistantMessage和ToolResponseMessage
        return UserMessage.builder()
                .text(entry.getContent())
                .build();

        // 完整实现需要等待Spring AI 2.0 API稳定后再修改
        // return switch (entry.getType()) {
        //     case CONVERSATION -> {
        //         if ("ASSISTANT".equalsIgnoreCase(source)) {
        //             yield AssistantMessage.builder()
        //                     .text(entry.getContent())
        //                     .build();
        //         } else {
        //             yield UserMessage.builder()
        //                     .text(entry.getContent())
        //                     .build();
        //         }
        //     }
        //     case SUMMARY -> {
        //         yield UserMessage.builder()
        //                 .text("[历史摘要] " + entry.getContent())
        //                 .build();
        //     }
        //     case TOOL_RESULT -> {
        //         String toolName = entry.getMetadata().get("toolName");
        //         yield UserMessage.builder()
        //                 .text("[" + (toolName != null ? toolName : "tool") + "] " + entry.getContent())
        //                 .build();
        //     }
        //     case FACT -> {
        //         yield UserMessage.builder()
        //                 .text("[用户偏好] " + entry.getContent())
        //                 .build();
        //     }
        //     default -> {
        //         log.warn("未知的记忆类型: {}, conversationId={}", entry.getType(), entry.getId());
        //         yield UserMessage.builder()
        //                 .text(entry.getContent())
        //                 .build();
        //     }
        // };
    }

    /**
     * 获取指定会话的消息数量
     */
    public long countMessages(String conversationId) {
        return redisUtils.getMemorySize(conversationId);
    }
}
