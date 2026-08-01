package com.itswy.paicodingai.memory.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis 短期记忆实现
 *
 * 使用 Redis List 存储 MemoryEntry，支持：
 * - FIFO 顺序存储
 * - Token 预算管理
 * - 自动淘汰最旧消息
 * - 7天 TTL 过期
 *
 * 存储结构：
 * Key: chat:memory:{conversationId}
 * Value: [MemoryEntry1.json, MemoryEntry2.json, ...]
 *
 * 设计原则：
 * 1. 通过 RedisUtils 统一管理 Redis 操作
 * 2. 键名由 RedisUtils 统一生成
 * 3. 便于后期切换存储方案
 *
 * 注意：不使用 @Component，由 SpringAIConfig 通过 @Bean 手动创建（构造器需要运行时参数）
 */
@Slf4j
public class RedisConversationMemory {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final String conversationId;
    private int maxTokens;
    private int currentTokens;
    private final List<MemoryEntry> compressedSummaries;

    /**
     * 构造函数
     */
    public RedisConversationMemory(RedisUtils redisUtils,
                                   ObjectMapper objectMapper,
                                   String conversationId,
                                   int maxTokens) {
        this.redisUtils = redisUtils;
        this.objectMapper = objectMapper;
        this.conversationId = conversationId;
        this.maxTokens = maxTokens;
        this.currentTokens = 0;
        this.compressedSummaries = new ArrayList<>();

        // 启动时加载现有消息，计算 currentTokens
        loadFromRedis();
    }

    /**
     * 存储一条记忆
     */
    public void store(MemoryEntry entry) {
        try {
            String key = redisUtils.memoryKey(conversationId);
            String json = objectMapper.writeValueAsString(entry);

            // 通过 RedisUtils 存入 Redis List
            redisUtils.opsForList().rightPush(key, json);

            // 更新 token 计数
            currentTokens += entry.getTokenCount();

            // 超出预算时淘汰最旧的消息
            while (currentTokens > maxTokens && size() > 1) {
                evictOldest();
            }

            log.debug("存储记忆: {} tokens, 当前: {}/{} tokens",
                    entry.getTokenCount(), currentTokens, maxTokens);

        } catch (Exception e) {
            log.error("存储记忆失败: conversationId={}", conversationId, e);
            throw new RuntimeException("存储记忆失败", e);
        }
    }

    /**
     * 获取所有记忆
     */
    public List<MemoryEntry> getAll() {
        try {
            String key = redisUtils.memoryKey(conversationId);
            List<String> jsonList = redisUtils.opsForList().all(key);

            if (jsonList.isEmpty()) {
                return new ArrayList<>();
            }

            return jsonList.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, MemoryEntry.class);
                        } catch (Exception e) {
                            log.error("反序列化记忆失败", e);
                            return null;
                        }
                    })
                    .filter(entry -> entry != null)
                    .toList();

        } catch (Exception e) {
            log.error("获取记忆失败: conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 清空所有记忆
     */
    public void clear() {
        try {
            redisUtils.clearMemory(conversationId);
            currentTokens = 0;
            compressedSummaries.clear();
            log.debug("清空记忆: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("清空记忆失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 获取记忆数量
     */
    public int size() {
        return (int) redisUtils.getMemorySize(conversationId);
    }

    /**
     * 获取当前 token 数
     */
    public int getTokenCount() {
        return currentTokens;
    }

    /**
     * 获取最大 token 预算
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * 设置最大 token 预算
     */
    public void setMaxTokens(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        this.maxTokens = maxTokens;

        // 超出新预算时淘汰
        while (currentTokens > maxTokens && size() > 1) {
            evictOldest();
        }
    }

    /**
     * 获取 token 使用率
     */
    public double getUsageRatio() {
        return maxTokens > 0 ? (double) currentTokens / maxTokens : 0;
    }

    /**
     * 获取压缩后的摘要列表
     */
    public List<MemoryEntry> getCompressedSummaries() {
        return Collections.unmodifiableList(compressedSummaries);
    }

    /**
     * 注入压缩后的摘要
     */
    public void injectSummary(MemoryEntry summary) {
        compressedSummaries.clear();
        store(summary);
    }

    /**
     * 淘汰最旧的一条记忆，并加入压缩摘要列表
     */
    private void evictOldest() {
        try {
            String key = redisUtils.memoryKey(conversationId);
            String json = redisUtils.opsForList().leftPop(key);

            if (json != null) {
                MemoryEntry oldest = objectMapper.readValue(json, MemoryEntry.class);
                currentTokens -= oldest.getTokenCount();
                compressedSummaries.add(oldest);

                log.debug("淘汰最旧记忆: {} tokens, 剩余: {}/{} tokens",
                        oldest.getTokenCount(), currentTokens, maxTokens);
            }

        } catch (Exception e) {
            log.error("淘汰记忆失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 从 Redis 加载现有消息
     */
    private void loadFromRedis() {
        try {
            List<MemoryEntry> all = getAll();
            currentTokens = all.stream()
                    .mapToInt(MemoryEntry::getTokenCount)
                    .sum();

            log.debug("加载记忆: {} 条, {} tokens", all.size(), currentTokens);

        } catch (Exception e) {
            log.error("加载记忆失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 生成记忆状态摘要
     */
    public String getStatusSummary() {
        return String.format("短期记忆: %d条 / %d tokens (预算: %d, 使用率: %.0f%%, 已压缩: %d条)",
                size(), currentTokens, maxTokens, getUsageRatio() * 100, compressedSummaries.size());
    }
}
