package com.itswy.paicodingai.memory.concurrent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 并发安全的 Redis 短期记忆实现
 *
 * 使用 RedisUtils 统一管理 Redis 操作
 * 支持：
 * - 分布式锁（多实例部署）
 * - 并发读写保护
 *
 * 适用场景：
 * - 多实例部署（负载均衡）
 * - 高并发写入
 * - 多线程访问同一会话
 *
 * 设计原则：
 * 1. 通过 RedisUtils 统一管理 Redis 操作
 * 2. 锁操作也通过 RedisUtils
 * 3. 便于后期切换存储方案
 */
@Slf4j
@Component
public class ConcurrentRedisConversationMemory {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    private static final long LOCK_WAIT_TIME = 5;      // 等待锁时间（秒）
    private static final long LOCK_LEASE_TIME = 30;    // 持有锁时间（秒）

    public ConcurrentRedisConversationMemory(RedisUtils redisUtils, ObjectMapper objectMapper) {
        this.redisUtils = redisUtils;
        this.objectMapper = objectMapper;
    }

    /**
     * 并发安全的存储方法
     */
    public void store(String conversationId, MemoryEntry entry) {
        try {
            // 尝试获取分布式锁
            if (redisUtils.tryLock(conversationId, LOCK_WAIT_TIME, LOCK_LEASE_TIME)) {
                try {
                    // 临界区：存入 Redis
                    String key = redisUtils.memoryKey(conversationId);
                    String json = objectMapper.writeValueAsString(entry);

                    // 通过 RedisUtils 存储
                    redisUtils.opsForList().rightPush(key, json);

                    log.debug("并发安全存储记忆: conversationId={}, {} tokens",
                            conversationId, entry.getTokenCount());

                } finally {
                    // 释放锁
                    redisUtils.releaseLock(conversationId);
                }
            } else {
                // 获取锁失败，降级为无锁写入（保证可用性）
                log.warn("获取分布式锁失败，降级为无锁写入: conversationId={}", conversationId);
                storeWithoutLock(conversationId, entry);
            }

        } catch (Exception e) {
            log.error("存储记忆失败: conversationId={}", conversationId, e);
            throw new RuntimeException("存储记忆失败", e);
        }
    }

    /**
     * 无锁写入（降级方案）
     */
    private void storeWithoutLock(String conversationId, MemoryEntry entry) {
        try {
            String key = redisUtils.memoryKey(conversationId);
            String json = objectMapper.writeValueAsString(entry);

            redisUtils.opsForList().rightPush(key, json);

            log.debug("无锁写入记忆: conversationId={}", conversationId);

        } catch (Exception e) {
            log.error("无锁写入失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 并发安全的获取所有记忆
     */
    public List<MemoryEntry> getAll(String conversationId) {
        // 读操作不需要加锁（Redis 单线程保证原子性）
        try {
            List<String> jsonList = redisUtils.getAllMemory(conversationId);

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
     * 并发安全的清空记忆
     */
    public void clear(String conversationId) {
        try {
            if (redisUtils.tryLock(conversationId, LOCK_WAIT_TIME, LOCK_LEASE_TIME)) {
                try {
                    redisUtils.clearMemory(conversationId);
                    log.debug("清空记忆: conversationId={}", conversationId);

                } finally {
                    redisUtils.releaseLock(conversationId);
                }
            } else {
                log.warn("获取锁失败，跳过清空: conversationId={}", conversationId);
            }

        } catch (Exception e) {
            log.error("清空记忆失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 获取消息数量
     */
    public long size(String conversationId) {
        return redisUtils.getMemorySize(conversationId);
    }
}
