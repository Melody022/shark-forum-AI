package com.itswy.paicodingai.memory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发安全测试
 */
@SpringBootTest
class ConcurrentMemoryTest {

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void testConcurrentStore() throws InterruptedException {
        String conversationId = "concurrent-test-" + System.currentTimeMillis();
        ConcurrentRedisConversationMemory memory = new ConcurrentRedisConversationMemory(
                redisTemplate, objectMapper, null
        );

        int threadCount = 10;
        int messagesPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * messagesPerThread);

        // 10个线程并发写入，每个线程写10条消息
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    try {
                        MemoryEntry entry = new MemoryEntry(
                                "concurrent-" + threadIndex + "-" + j,
                                "并发测试消息 " + threadIndex + "-" + j,
                                MemoryEntry.MemoryType.CONVERSATION,
                                java.util.Map.of("source", "user"),
                                10
                        );
                        memory.store(conversationId, entry);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // 等待所有线程完成
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 验证最终状态
        long size = memory.size(conversationId);
        assertTrue(size > 0, "应该有消息被存储");
        assertTrue(size <= threadCount * messagesPerThread, "消息数量不应该超过预期");
    }

    @Test
    void testConcurrentStoreWithRedisson() throws InterruptedException {
        String conversationId = "concurrent-redisson-" + System.currentTimeMillis();
        RedisConversationMemory memory = new RedisConversationMemory(
                redisTemplate, objectMapper, conversationId, 10000
        );

        int threadCount = 10;
        int messagesPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * messagesPerThread);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    try {
                        MemoryEntry entry = new MemoryEntry(
                                "redisson-" + threadIndex + "-" + j,
                                "Redisson并发测试消息 " + threadIndex + "-" + j,
                                MemoryEntry.MemoryType.CONVERSATION,
                                java.util.Map.of("source", "user"),
                                10
                        );
                        memory.store(entry);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // 验证
        assertTrue(memory.size() > 0);
        assertTrue(memory.getTokenCount() <= 10000);
    }

    @Test
    void testTokenBudgetAtomicity() {
        String conversationId = "token-atomic-" + System.currentTimeMillis();
        AtomicTokenBudget budget = new AtomicTokenBudget(redisTemplate);

        int threadCount = 10;
        int tokensPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    budget.recordUsage(conversationId, tokensPerThread, tokensPerThread / 2);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        // 验证原子性
        long totalInput = budget.getTotalInputTokens(conversationId);
        long totalOutput = budget.getTotalOutputTokens(conversationId);
        long callCount = budget.getLlmCallCount(conversationId);

        assertEquals(threadCount * tokensPerThread, totalInput, "输入token总数应该正确");
        assertEquals(threadCount * (tokensPerThread / 2), totalOutput, "输出token总数应该正确");
        assertEquals(threadCount, callCount, "调用次数应该正确");
    }

    @Test
    void testUsageReport() {
        String conversationId = "usage-report-" + System.currentTimeMillis();
        AtomicTokenBudget budget = new AtomicTokenBudget(redisTemplate);

        // 记录几次使用
        budget.recordUsage(conversationId, 1000, 500);
        budget.recordUsage(conversationId, 2000, 1000);

        String report = budget.getUsageReport(conversationId);

        assertNotNull(report);
        assertTrue(report.contains("Token 统计"));
        assertTrue(report.contains("2 次"));  // 调用2次
        assertTrue(report.contains("3000")); // 总输入3000
    }
}
