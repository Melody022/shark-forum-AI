package com.itswy.paicodingai.memory.service;

import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token 预算管理器 - 确保对话不会超出模型的上下文窗口
 *
 * 功能：
 * 1. 设定总 token 预算（系统提示 + 工具定义 + 对话历史 + 回复预留）
 * 2. 每次调用 LLM 前检查预算
 * 3. 超出预算时触发压缩或裁剪
 * 4. 记录 token 消耗统计
 *
 * 配置示例：
 * paicoding.ai.memory.token-budget.context-window=1048576
 * paicoding.ai.memory.token-budget.compression-threshold=0.9
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "paicoding.ai.memory.token-budget")
public class TokenBudget {
    private int contextWindow = 1048576;          // 模型上下文窗口（默认1M）
    private int reservedForSystem = 1000;         // 系统提示预留
    private int reservedForTools = 2000;          // 工具定义预留
    private int reservedForResponse = 4000;       // 回复预留
    private double compressionThreshold = 0.9;    // 压缩触发阈值（默认90%）

    // 累计 token 消耗统计（原子计数器，线程安全）
    private final AtomicInteger totalInputTokens = new AtomicInteger(0);
    private final AtomicInteger totalOutputTokens = new AtomicInteger(0);
    private final AtomicInteger totalCachedInputTokens = new AtomicInteger(0);
    private final AtomicInteger llmCallCount = new AtomicInteger(0);

    /**
     * 获取对话历史可用的 token 预算
     */
    public int getAvailableForConversation() {
        return contextWindow - reservedForSystem - reservedForTools - reservedForResponse;
    }

    /**
     * 检查是否需要压缩
     *
     * @param memory        短期记忆
     * @param triggerRatio  触发压缩的占用率（0.0-1.0）
     * @return 是否需要压缩
     */
    public boolean needsCompression(RedisConversationMemory memory, double triggerRatio) {
        int compressionBudget = Math.min(memory.getMaxTokens(), getAvailableForConversation());
        return memory.getTokenCount() >= compressionBudget * triggerRatio;
    }

    /**
     * 检查是否需要压缩（使用默认阈值）
     */
    public boolean needsCompression(RedisConversationMemory memory) {
        return needsCompression(memory, compressionThreshold);
    }

    /**
     * 记录一次 LLM 调用的 token 消耗
     */
    public void recordUsage(int inputTokens, int outputTokens) {
        recordUsage(inputTokens, outputTokens, 0);
    }

    /**
     * 记录一次 LLM 调用的 token 消耗（包含缓存）
     */
    public void recordUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        totalInputTokens.addAndGet(inputTokens);
        totalOutputTokens.addAndGet(outputTokens);
        totalCachedInputTokens.addAndGet(Math.max(0, cachedInputTokens));
        llmCallCount.incrementAndGet();

        log.debug("记录 token 消耗: input={}, output={}, cached={}, 累计调用={}",
                inputTokens, outputTokens, cachedInputTokens, llmCallCount.get());
    }

    /**
     * 获取 token 使用统计报告
     */
    public String getUsageReport() {
        int avgInput = llmCallCount.get() > 0
                ? totalInputTokens.get() / llmCallCount.get()
                : 0;

        return String.format(
                "Token 统计: 调用 %d 次 | 总输入: %d | 总输出: %d | cached: %d | 平均输入: %d | 预算: %d (可用: %d)",
                llmCallCount.get(),
                totalInputTokens.get(),
                totalOutputTokens.get(),
                totalCachedInputTokens.get(),
                avgInput,
                contextWindow,
                getAvailableForConversation()
        );
    }

    /**
     * 估算消息列表的 token 总数
     */
    public static int estimateMessagesTokens(java.util.List<String> messages) {
        if (messages == null || messages.isEmpty()) return 0;

        int total = 0;
        for (String message : messages) {
            total += MemoryEntry.estimateTokens(message);
        }

        // 每条消息额外开销约 4 tokens（role、separator 等）
        total += messages.size() * 4;

        return total;
    }

    // Getters and Setters
    public int getContextWindow() { return contextWindow; }
    public void setContextWindow(int contextWindow) { this.contextWindow = contextWindow; }

    public int getReservedForSystem() { return reservedForSystem; }
    public void setReservedForSystem(int reservedForSystem) { this.reservedForSystem = reservedForSystem; }

    public int getReservedForTools() { return reservedForTools; }
    public void setReservedForTools(int reservedForTools) { this.reservedForTools = reservedForTools; }

    public int getReservedForResponse() { return reservedForResponse; }
    public void setReservedForResponse(int reservedForResponse) { this.reservedForResponse = reservedForResponse; }

    public double getCompressionThreshold() { return compressionThreshold; }
    public void setCompressionThreshold(double compressionThreshold) { this.compressionThreshold = compressionThreshold; }

    public int getTotalInputTokens() { return totalInputTokens.get(); }
    public int getTotalOutputTokens() { return totalOutputTokens.get(); }
    public int getTotalCachedInputTokens() { return totalCachedInputTokens.get(); }
    public int getLlmCallCount() { return llmCallCount.get(); }
}
