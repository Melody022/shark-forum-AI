package com.itswy.paicodingai.memory.manager;

import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import com.itswy.paicodingai.memory.service.ContextCompressor;
import com.itswy.paicodingai.memory.service.TokenAwareChatMemory;
import com.itswy.paicodingai.memory.service.TokenBudget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Memory 管理器 - 记忆系统的门面类
 *
 * 使用TokenAwareChatMemory管理多个会话的记忆
 * MemoryManager只负责协调，不直接操作存储
 */
@Slf4j
@Component
public class MemoryManager {

    private final TokenAwareChatMemory tokenAwareChatMemory;
    private final TokenBudget tokenBudget;
    private final ContextCompressor compressor;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private static final int MAX_TOOL_RESULT_CHARS = 500;

    public MemoryManager(TokenAwareChatMemory tokenAwareChatMemory,
                         TokenBudget tokenBudget,
                         ContextCompressor compressor) {
        this.tokenAwareChatMemory = tokenAwareChatMemory;
        this.tokenBudget = tokenBudget;
        this.compressor = compressor;
    }

    public void addUserMessage(String conversationId, String content) {
        rwLock.writeLock().lock();
        try {
            MemoryEntry entry = new MemoryEntry(
                    "user-" + UUID.randomUUID().toString().substring(0, 8),
                    content,
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of("source", "user"),
                    MemoryEntry.estimateTokens(content)
            );
            RedisConversationMemory memory = tokenAwareChatMemory.getOrCreateMemory(conversationId);
            memory.store(entry);
            log.debug("添加用户消息: conversationId={}, tokens={}", conversationId, entry.getTokenCount());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void addAssistantMessage(String conversationId, String content) {
        rwLock.writeLock().lock();
        try {
            MemoryEntry entry = new MemoryEntry(
                    "assistant-" + UUID.randomUUID().toString().substring(0, 8),
                    content,
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of("source", "assistant"),
                    MemoryEntry.estimateTokens(content)
            );
            RedisConversationMemory memory = tokenAwareChatMemory.getOrCreateMemory(conversationId);
            memory.store(entry);
            log.debug("添加助手回复: conversationId={}, tokens={}", conversationId, entry.getTokenCount());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void addToolResult(String conversationId, String toolName, String result) {
        rwLock.writeLock().lock();
        try {
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
            RedisConversationMemory memory = tokenAwareChatMemory.getOrCreateMemory(conversationId);
            memory.store(entry);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<MemoryEntry> getAll(String conversationId) {
        rwLock.readLock().lock();
        try {
            RedisConversationMemory memory = tokenAwareChatMemory.getOrCreateMemory(conversationId);
            return memory.getAll();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public RedisConversationMemory getShortTermMemory() {
        return tokenAwareChatMemory.getOrCreateMemory("default");
    }

    public boolean compressIfNeeded(String conversationId) {
        rwLock.writeLock().lock();
        try {
            RedisConversationMemory memory = tokenAwareChatMemory.getOrCreateMemory(conversationId);
            if (!tokenBudget.needsCompression(memory)) {
                return false;
            }
            log.info("Token占用超过阈值，触发压缩: conversationId={}", conversationId);
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void clearShortTerm(String conversationId) {
        rwLock.writeLock().lock();
        try {
            tokenAwareChatMemory.clear(conversationId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
