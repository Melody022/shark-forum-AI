package com.itswy.paicodingai.memory.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * 记忆条目 - Memory 系统的基础数据单元
 *
 * 统一的数据结构，支持不同类型的记忆：
 * - CONVERSATION：普通对话消息
 * - FACT：事实记忆（用户偏好、项目配置等）
 * - SUMMARY：压缩后的历史摘要
 * - TOOL_RESULT：工具调用返回结果
 */
public class MemoryEntry {
    private final String id;
    private final String content;
    private final MemoryType type;
    private final Instant timestamp;
    private final Map<String, String> metadata;
    private final int tokenCount;

    /**
     * 记忆类型枚举
     */
    public enum MemoryType {
        CONVERSATION,  // 对话记忆
        FACT,          // 事实记忆（用户偏好、项目信息等）
        SUMMARY,       // 摘要记忆
        TOOL_RESULT    // 工具执行结果
    }

    /**
     * 构造函数（使用当前时间）
     */
    public MemoryEntry(String id, String content, MemoryType type, Map<String, String> metadata, int tokenCount) {
        this(id, content, type, Instant.now(), metadata, tokenCount);
    }

    /**
     * 构造函数（指定时间戳）
     */
    @JsonCreator
    public MemoryEntry(@JsonProperty("id") String id,
                       @JsonProperty("content") String content,
                       @JsonProperty("type") MemoryType type,
                       @JsonProperty("timestamp") Instant timestamp,
                       @JsonProperty("metadata") Map<String, String> metadata,
                       @JsonProperty("tokenCount") int tokenCount) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.metadata = metadata != null ? metadata : Map.of();
        this.tokenCount = tokenCount;
    }

    /**
     * 粗略估算 token 数
     *
     * 规则：
     * - 中文字符：1.5 个字 = 1 Token
     * - 英文字符/符号：4 个字符 = 1 Token
     * - 公式向上取整
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.chars().filter(c -> c > 0x4E00 && c < 0x9FFF).count();
        long otherChars = text.length() - chineseChars;
        return (int) Math.ceil(chineseChars / 1.5 + otherChars / 4.0);
    }

    // Getters
    public String getId() { return id; }
    public String getContent() { return content; }
    public MemoryType getType() { return type; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, String> getMetadata() { return metadata; }
    public int getTokenCount() { return tokenCount; }

    @Override
    public String toString() {
        return "[%s] %s: %s".formatted(type, id,
                content.length() > 80 ? content.substring(0, 80) + "..." : content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryEntry that = (MemoryEntry) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
