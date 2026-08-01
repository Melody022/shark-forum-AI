package com.itswy.paicodingai.memory.concurrent;

import com.itswy.paicodingai.memory.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 并发安全的 Token 预算统计
 *
 * 使用 RedisUtils 统一管理 Redis 操作
 * 适用于多实例部署场景，跨实例统计 token 消耗
 *
 * 存储结构：
 * Key: token:count:{conversationId}
 * Field: input / output / count
 * Value: 数值
 *
 * 设计原则：
 * 1. 通过 RedisUtils 统一管理 Redis 操作
 * 2. 使用 Hash 原子递增，保证并发安全
 * 3. 便于后期切换存储方案
 */
@Slf4j
@Component
public class AtomicTokenBudget {

    private final RedisUtils redisUtils;

    public AtomicTokenBudget(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    /**
     * 记录 token 消耗（原子递增）
     */
    public void recordUsage(String conversationId, int inputTokens, int outputTokens) {
        try {
            redisUtils.recordTokenUsage(conversationId, inputTokens, outputTokens);

            log.debug("记录 token 消耗: conversationId={}, input={}, output={}",
                    conversationId, inputTokens, outputTokens);

        } catch (Exception e) {
            log.error("记录 token 消耗失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 获取 token 使用统计
     */
    public Map<String, Long> getUsage(String conversationId) {
        return redisUtils.getTokenUsage(conversationId);
    }

    /**
     * 获取总输入 token 数
     */
    public long getTotalInputTokens(String conversationId) {
        return redisUtils.getTotalInputTokens(conversationId);
    }

    /**
     * 获取总输出 token 数
     */
    public long getTotalOutputTokens(String conversationId) {
        return redisUtils.getTotalOutputTokens(conversationId);
    }

    /**
     * 获取调用次数
     */
    public long getLlmCallCount(String conversationId) {
        return redisUtils.getLlmCallCount(conversationId);
    }

    /**
     * 获取使用报告
     */
    public String getUsageReport(String conversationId) {
        Map<String, Long> usage = getUsage(conversationId);
        long input = usage.getOrDefault("input", 0L);
        long output = usage.getOrDefault("output", 0L);
        long count = usage.getOrDefault("count", 0L);
        long avgInput = count > 0 ? input / count : 0;

        return String.format(
                "Token 统计 [%s]: 调用 %d 次 | 总输入: %d | 总输出: %d | 平均输入: %d",
                conversationId, count, input, output, avgInput
        );
    }

    /**
     * 清空统计
     */
    public void clear(String conversationId) {
        redisUtils.clearTokenUsage(conversationId);
        log.debug("清空 token 统计: conversationId={}", conversationId);
    }
}
