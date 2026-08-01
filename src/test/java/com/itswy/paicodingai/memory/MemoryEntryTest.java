package com.itswy.paicodingai.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryEntry 单元测试
 */
class MemoryEntryTest {

    @Test
    void testTokenEstimation_Chinese() {
        // 中文：1.5字=1token
        assertEquals(2, MemoryEntry.estimateTokens("你好世界"));
        assertEquals(3, MemoryEntry.estimateTokens("这是一个测试消息"));
    }

    @Test
    void testTokenEstimation_English() {
        // 英文：4字符=1token
        assertEquals(3, MemoryEntry.estimateTokens("Hello World"));
        assertEquals(2, MemoryEntry.estimateTokens("Hi"));
    }

    @Test
    void testTokenEstimation_Mixed() {
        // 混合
        assertTrue(MemoryEntry.estimateTokens("Hello 你好") > 0);
        assertTrue(MemoryEntry.estimateTokens("Spring AI 是一个框架") > 0);
    }

    @Test
    void testTokenEstimation_Empty() {
        assertEquals(0, MemoryEntry.estimateTokens(null));
        assertEquals(0, MemoryEntry.estimateTokens(""));
    }

    @Test
    void testCreateConversationEntry() {
        MemoryEntry entry = new MemoryEntry(
                "user-1234",
                "你好，我想学习Spring AI",
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", "user"),
                MemoryEntry.estimateTokens("你好，我想学习Spring AI")
        );

        assertNotNull(entry);
        assertEquals("user-1234", entry.getId());
        assertEquals("你好，我想学习Spring AI", entry.getContent());
        assertEquals(MemoryEntry.MemoryType.CONVERSATION, entry.getType());
        assertNotNull(entry.getTimestamp());
        assertEquals("user", entry.getMetadata().get("source"));
        assertTrue(entry.getTokenCount() > 0);
    }

    @Test
    void testCreateSummaryEntry() {
        MemoryEntry entry = new MemoryEntry(
                "summary-5678",
                "[历史对话摘要] 用户询问了关于Spring AI的问题...",
                MemoryEntry.MemoryType.SUMMARY,
                Instant.now(),
                Map.of("source", "compressor"),
                MemoryEntry.estimateTokens("[历史对话摘要] 用户询问了关于Spring AI的问题...")
        );

        assertNotNull(entry);
        assertEquals("summary-5678", entry.getId());
        assertEquals(MemoryEntry.MemoryType.SUMMARY, entry.getType());
    }

    @Test
    void testCreateToolResultEntry() {
        MemoryEntry entry = new MemoryEntry(
                "tool-9012",
                "[search] 搜索结果：找到3篇文章...",
                MemoryEntry.MemoryType.TOOL_RESULT,
                Map.of("source", "tool", "toolName", "search"),
                MemoryEntry.estimateTokens("[search] 搜索结果：找到3篇文章...")
        );

        assertNotNull(entry);
        assertEquals(MemoryEntry.MemoryType.TOOL_RESULT, entry.getType());
        assertEquals("search", entry.getMetadata().get("toolName"));
    }

    @Test
    void testEqualsAndHashCode() {
        MemoryEntry entry1 = new MemoryEntry(
                "user-1234",
                "内容1",
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of(),
                10
        );

        MemoryEntry entry2 = new MemoryEntry(
                "user-1234",
                "内容2",
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of(),
                20
        );

        MemoryEntry entry3 = new MemoryEntry(
                "user-5678",
                "内容1",
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of(),
                10
        );

        // 相同ID应该相等
        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());

        // 不同ID不相等
        assertNotEquals(entry1, entry3);
    }

    @Test
    void testToString() {
        MemoryEntry entry = new MemoryEntry(
                "user-1234",
                "这是一个很长的消息，用于测试toString方法是否会截断...",
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of(),
                10
        );

        String str = entry.toString();
        assertTrue(str.contains("CONVERSATION"));
        assertTrue(str.contains("user-1234"));
    }
}
