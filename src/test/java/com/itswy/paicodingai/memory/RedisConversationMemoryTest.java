package com.itswy.paicodingai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisConversationMemory 单元测试
 */
@SpringBootTest
class RedisConversationMemoryTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private RedisConversationMemory memory;

    @BeforeEach
    void setUp() {
        // 每个测试使用不同的conversationId
        String conversationId = "test-" + System.currentTimeMillis();
        memory = new RedisConversationMemory(redisTemplate, objectMapper, conversationId, 1000);
    }

    @Test
    void testStoreAndRetrieve() {
        MemoryEntry entry = new MemoryEntry(
                "test-1",
                "你好，我想学习Spring AI",
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of("source", "user"),
                MemoryEntry.estimateTokens("你好，我想学习Spring AI")
        );

        memory.store(entry);

        assertEquals(1, memory.size());
        assertTrue(memory.getTokenCount() > 0);

        var all = memory.getAll();
        assertEquals(1, all.size());
        assertEquals("你好，我想学习Spring AI", all.get(0).getContent());
    }

    @Test
    void testStoreMultipleEntries() {
        for (int i = 0; i < 5; i++) {
            MemoryEntry entry = new MemoryEntry(
                    "test-" + i,
                    "消息 " + i,
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of("source", "user"),
                    MemoryEntry.estimateTokens("消息 " + i)
            );
            memory.store(entry);
        }

        assertEquals(5, memory.size());
        assertTrue(memory.getTokenCount() > 0);
    }

    @Test
    void testEviction() {
        // 创建一个预算很小的记忆
        RedisConversationMemory smallMemory = new RedisConversationMemory(
                redisTemplate, objectMapper, "eviction-test-" + System.currentTimeMillis(), 100
        );

        // 存入多条消息，每条约50 tokens
        for (int i = 0; i < 10; i++) {
            MemoryEntry entry = new MemoryEntry(
                    "test-" + i,
                    "这是一条测试消息，内容较长以确保token数足够，我们需要更多的文本来增加token数量",
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of("source", "user"),
                    50  // 每条50 tokens
            );
            smallMemory.store(entry);
        }

        // 应该被淘汰到只剩最近的几条
        assertTrue(smallMemory.size() < 10, "应该淘汰旧消息");
        assertTrue(smallMemory.getTokenCount() <= 100, "不应该超过预算");
    }

    @Test
    void testClear() {
        for (int i = 0; i < 3; i++) {
            MemoryEntry entry = new MemoryEntry(
                    "test-" + i,
                    "消息 " + i,
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of(),
                    10
            );
            memory.store(entry);
        }

        assertEquals(3, memory.size());

        memory.clear();

        assertEquals(0, memory.size());
        assertEquals(0, memory.getTokenCount());
    }

    @Test
    void testGetUsageRatio() {
        // 初始使用率应该为0
        assertEquals(0.0, memory.getUsageRatio(), 0.01);

        // 存入一些消息
        for (int i = 0; i < 5; i++) {
            MemoryEntry entry = new MemoryEntry(
                    "test-" + i,
                    "消息 " + i,
                    MemoryEntry.MemoryType.CONVERSATION,
                    Map.of(),
                    100  // 每条100 tokens
            );
            memory.store(entry);
        }

        // 使用率应该大于0
        assertTrue(memory.getUsageRatio() > 0);
    }

    @Test
    void testGetStatusSummary() {
        MemoryEntry entry = new MemoryEntry(
                "test-1",
                "测试消息",
                MemoryEntry.MemoryType.CONVERSATION,
                Map.of(),
                10
        );
        memory.store(entry);

        String status = memory.getStatusSummary();

        assertNotNull(status);
        assertTrue(status.contains("短期记忆"));
        assertTrue(status.contains("tokens"));
    }

    @Test
    void testSetMaxTokens() {
        // 初始预算1000
        assertEquals(1000, memory.getMaxTokens());

        // 设置新预算
        memory.setMaxTokens(500);
        assertEquals(500, memory.getMaxTokens());

        // 测试无效值
        assertThrows(IllegalArgumentException.class, () -> memory.setMaxTokens(0));
        assertThrows(IllegalArgumentException.class, () -> memory.setMaxTokens(-1));
    }
}
