package com.itswy.paicodingai.rag.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量Embedding服务
 *
 * 参考派聪明实现：
 * - 批量调用（默认10条/批）
 * - 自动重试（3次，间隔1秒）
 * - 速率限制（60次/分钟）
 * - 用量配额跟踪
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchEmbeddingService {

    private final EmbeddingModel embeddingModel;

    /** 批量大小 */
    private static final int BATCH_SIZE = 10;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 重试间隔（毫秒） */
    private static final long RETRY_INTERVAL = 1000;

    /** 速率限制（次/分钟） */
    private static final int RATE_LIMIT = 60;

    /** 调用计数器（用于速率限制） */
    private final AtomicInteger callCount = new AtomicInteger(0);

    /** 上次重置时间 */
    private volatile long lastResetTime = System.currentTimeMillis();

    /**
     * 批量向量化
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        log.info("开始批量向量化，文本数量: {}", texts.size());

        List<float[]> allEmbeddings = new ArrayList<>();

        // 分批处理
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);

            // 速率限制检查
            checkRateLimit();

            // 带重试的批量调用
            List<float[]> batchEmbeddings = embedWithRetry(batch);
            allEmbeddings.addAll(batchEmbeddings);

            log.debug("批次 {}/{} 完成，向量化数量: {}",
                      (i / BATCH_SIZE + 1), (texts.size() + BATCH_SIZE - 1) / BATCH_SIZE,
                      batchEmbeddings.size());
        }

        log.info("批量向量化完成，总数量: {}", allEmbeddings.size());
        return allEmbeddings;
    }

    /**
     * 单条向量化
     *
     * @param text 文本
     * @return 向量
     */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[0];
        }

        checkRateLimit();
        return embedWithRetry(List.of(text)).get(0);
    }

    /**
     * 带重试的批量调用
     */
    private List<float[]> embedWithRetry(List<String> texts) {
        int retries = 0;
        Exception lastException = null;

        while (retries < MAX_RETRIES) {
            try {
                // 构建请求
                EmbeddingRequest request = new EmbeddingRequest(texts, null);
                EmbeddingResponse response = embeddingModel.call(request);

                // 提取向量
                List<float[]> embeddings = response.getResults().stream()
                    .map(result -> {
                        float[] embedding = new float[result.getOutput().length];
                        for (int i = 0; i < result.getOutput().length; i++) {
                            embedding[i] = (float) result.getOutput()[i];
                        }
                        return embedding;
                    })
                    .toList();

                // 更新调用计数
                callCount.incrementAndGet();

                return embeddings;

            } catch (Exception e) {
                lastException = e;
                retries++;

                if (retries < MAX_RETRIES) {
                    log.warn("Embedding调用失败，第{}次重试，错误: {}",
                             retries, e.getMessage());
                    try {
                        Thread.sleep(RETRY_INTERVAL * retries); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }

        log.error("Embedding调用失败，已重试{}次", MAX_RETRIES, lastException);
        throw new RuntimeException("Embedding调用失败: " + lastException.getMessage(), lastException);
    }

    /**
     * 速率限制检查
     */
    private void checkRateLimit() {
        long currentTime = System.currentTimeMillis();

        // 每分钟重置计数器
        if (currentTime - lastResetTime >= 60000) {
            callCount.set(0);
            lastResetTime = currentTime;
        }

        // 检查是否超过速率限制
        if (callCount.get() >= RATE_LIMIT) {
            long waitTime = 60000 - (currentTime - lastResetTime);
            if (waitTime > 0) {
                log.warn("达到速率限制，等待{}ms", waitTime);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                callCount.set(0);
                lastResetTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * 获取调用统计
     */
    public EmbeddingStats getStats() {
        return new EmbeddingStats(
            callCount.get(),
            RATE_LIMIT,
            BATCH_SIZE,
            MAX_RETRIES
        );
    }

    /**
     * 调用统计
     */
    public record EmbeddingStats(
        int totalCalls,
        int rateLimit,
        int batchSize,
        int maxRetries
    ) {}
}
