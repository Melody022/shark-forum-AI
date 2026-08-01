package com.itswy.paicodingai.memory.manager;

import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import com.itswy.paicodingai.memory.service.ContextCompressor;
import com.itswy.paicodingai.memory.service.TokenBudget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Memory 管理器 —— 记忆系统的门面类，为 Agent 提供统一的记忆存取接口
 *
 * 记忆系统分为三层：
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  短期记忆 (RedisConversationMemory)                                 │
 * │  - 存储当前会话的用户输入、AI 回复、工具结果                          │
 * │  - 会话关闭后丢失（7天TTL）                                         │
 * │  - 有 token 预算限制，超限会自动压缩                                 │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  长期记忆 (LongTermMemory) - 第三期实现                              │
 * │  - 存储跨会话的关键事实（如"用户喜欢 JDK 17"）                       │
 * │  - 持久化到 MySQL                                                    │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  外部记忆 (RAG) - 第三期实现                                         │
 * │  - Elasticsearch 向量检索                                            │
 * │  - 外部知识库注入                                                    │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Agent 使用记忆的流程：
 * 1. 用户输入 → addUserMessage() 记录短期记忆
 * 2. 执行过程中 → addAssistantMessage() / addToolResult() 记录
 * 3. 自动检查是否需要压缩 → compressIfNeeded()
 * 4. 下次请求 → 从短期记忆中检索上下文
 */
@Slf4j
@Component
public class MemoryManager {

    /** 短期记忆：存储当前会话的对话上下文 */
    private final RedisConversationMemory shortTermMemory;

    /** 上下文压缩器：当 token 超限时，用 LLM 生成摘要替代原始对话 */
    private final ContextCompressor compressor;

    /** Token 预算管理器：跟踪 token 使用量 */
    private final TokenBudget tokenBudget;

    /** 读写锁，保证并发安全 */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // 工具结果在记忆中的最大长度（完整结果已在任务消息历史里，记忆只需保留摘要）
    private static final int MAX_TOOL_RESULT_CHARS = 500;

    /**
     * 构造函数
     */
    public MemoryManager(RedisConversationMemory shortTermMemory,
                         ContextCompressor compressor,
                         TokenBudget tokenBudget) {
        this.shortTermMemory = shortTermMemory;
        this.compressor = compressor;
        this.tokenBudget = tokenBudget;
    }

    /**
     * 添加用户消息到短期记忆
     */
    public void addUserMessage(String content) {
        rwLock.writeLock().lock();
        try {
            MemoryEntry entry = new MemoryEntry(
                    "user-" + UUID.randomUUID().toString().substring(0, 8),
                    content,
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of("source", "user"),
                    MemoryEntry.estimateTokens(content)
            );

            shortTermMemory.store(entry);
            compressIfNeeded();

            log.debug("添加用户消息: {} tokens", entry.getTokenCount());

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 添加助手回复到短期记忆
     */
    public void addAssistantMessage(String content) {
        rwLock.writeLock().lock();
        try {
            MemoryEntry entry = new MemoryEntry(
                    "assistant-" + UUID.randomUUID().toString().substring(0, 8),
                    content,
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of("source", "assistant"),
                    MemoryEntry.estimateTokens(content)
            );

            shortTermMemory.store(entry);
            compressIfNeeded();

            log.debug("添加助手回复: {} tokens", entry.getTokenCount());

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 添加工具执行结果到短期记忆（截断过长结果，避免快速撑满预算）
     */
    public void addToolResult(String toolName, String result) {
        rwLock.writeLock().lock();
        try {
            // 截断过长结果
            String truncated = result.length() > MAX_TOOL_RESULT_CHARS
                    ? result.substring(0, MAX_TOOL_RESULT_CHARS) + "...(已截断)"
                    : result;
            String content = "[" + toolName + "] " + truncated;

            MemoryEntry entry = new MemoryEntry(
                    "tool-" + UUID.randomUUID().toString().substring(0, 8),
                    content,
                    MemoryEntry.MemoryType.TOOL_RESULT,
                    Map.of("source", "tool", "toolName", toolName),
                    MemoryEntry.estimateTokens(content)
            );

            shortTermMemory.store(entry);
            compressIfNeeded();

            log.debug("添加工具结果: {} tokens, tool={}", entry.getTokenCount(), toolName);

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取所有记忆
     */
    public List<MemoryEntry> getAll() {
        rwLock.readLock().lock();
        try {
            return shortTermMemory.getAll();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 构建用于 LLM 的记忆上下文
     */
    public String buildContextForQuery(String query, int maxTokens) {
        rwLock.readLock().lock();
        try {
            // 简化版：返回最近的 N 条消息
            List<MemoryEntry> all = shortTermMemory.getAll();
            StringBuilder context = new StringBuilder();

            int tokens = 0;
            for (int i = all.size() - 1; i >= 0 && tokens < maxTokens; i--) {
                MemoryEntry entry = all.get(i);
                tokens += entry.getTokenCount();
                context.insert(0, entry.getContent() + "\n");
            }

            return context.toString();

        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 记录 token 使用
     */
    public void recordTokenUsage(int inputTokens, int outputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens);
    }

    public void recordTokenUsage(int inputTokens, int outputTokens, int cachedInputTokens) {
        tokenBudget.recordUsage(inputTokens, outputTokens, cachedInputTokens);
    }

    /**
     * 检查并触发压缩（由 Agent 在 LLM 调用前主动调用）
     *
     * @return 是否执行了压缩
     */
    public boolean compressIfNeeded() {
        rwLock.writeLock().lock();
        try {
            if (!tokenBudget.needsCompression(shortTermMemory)) {
                return false;
            }

            int beforeTokens = shortTermMemory.getTokenCount();
            log.info("上下文占用达到压缩阈值（{}%），触发短期记忆压缩",
                    (int) (tokenBudget.getCompressionThreshold() * 100));

            String summary = compressor.compress(shortTermMemory);

            if (summary != null) {
                int afterTokens = shortTermMemory.getTokenCount();
                String preview = summary.substring(0, Math.min(100, summary.length()));
                log.info("短期记忆压缩完成: {} -> {} tokens, summaryPreview={}",
                        beforeTokens, afterTokens, preview);
            }

            return summary != null;

        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 清空短期记忆（保留长期记忆）
     */
    public void clearShortTerm() {
        rwLock.writeLock().lock();
        try {
            shortTermMemory.clear();
            log.info("清空短期记忆");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取记忆系统的整体状态
     */
    public String getSystemStatus() {
        rwLock.readLock().lock();
        try {
            return "上下文策略:\n" +
                    shortTermMemory.getStatusSummary() + "\n" +
                    tokenBudget.getUsageReport() + "\n" +
                    compressor.getConfigSummary();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // Getter
    public RedisConversationMemory getShortTermMemory() { return shortTermMemory; }
    public TokenBudget getTokenBudget() { return tokenBudget; }
}
