package com.itswy.paicodingai.memory.service;

import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import com.itswy.paicodingai.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 上下文压缩器 - 当对话过长时，自动压缩旧消息
 *
 * 压缩策略：
 * 1. Map-Reduce：先将旧消息分片摘要（Map），再合并摘要（Reduce）
 * 2. 保留最近 N 轮完整消息（不压缩）
 * 3. 压缩后的摘要回注到 ConversationMemory
 *
 * 配置示例：
 * paicoding.ai.memory.compressor.retain-recent-rounds=3
 * paicoding.ai.memory.compressor.chunk-size=5
 */
@Slf4j
@Component
public class ContextCompressor {

    private final ChatService chatService;

    @Value("${paicoding.ai.memory.compressor.retain-recent-rounds:3}")
    private int retainRecentRounds = 3;

    @Value("${paicoding.ai.memory.compressor.chunk-size:5}")
    private int chunkSize = 5;

    private static final String MAP_PROMPT = """
            请将以下对话片段压缩成一段简洁的摘要，保留关键信息：
            - 用户的需求和意图
            - 已执行的操作和结果
            - 做出的决策和结论
            - 重要的技术细节

            对话片段：
            %s

            请用中文输出摘要，控制在200字以内。
            """;

    private static final String REDUCE_PROMPT = """
            请将以下多个摘要合并成一个整体摘要，保留所有关键信息。

            各片段摘要：
            %s

            请用中文输出合并摘要，控制在300字以内。
            """;

    public ContextCompressor(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 压缩对话记忆
     *
     * @param memory 短期记忆
     * @return 压缩后的摘要，如果不需要压缩则返回 null
     */
    public String compress(RedisConversationMemory memory) {
        List<MemoryEntry> allEntries = memory.getAll();

        if (allEntries.size() <= retainRecentRounds) {
            log.debug("条目数 {} <= {}，跳过压缩", allEntries.size(), retainRecentRounds);
            return null;
        }

        // 分割：旧消息 vs 近期消息（必须拷贝，因为后面会 clear 底层集合）
        int splitPoint = allEntries.size() - retainRecentRounds;
        List<MemoryEntry> oldEntries = new ArrayList<>(allEntries.subList(0, splitPoint));
        List<MemoryEntry> recentEntries = new ArrayList<>(allEntries.subList(splitPoint, allEntries.size()));

        log.info("开始压缩：旧消息 {} 条，保留最近 {} 条", oldEntries.size(), recentEntries.size());

        // Map 阶段：分片摘要
        List<String> chunkSummaries = mapPhase(oldEntries);
        if (chunkSummaries.isEmpty()) {
            return null;
        }

        // Reduce 阶段：合并摘要
        String finalSummary;
        if (chunkSummaries.size() == 1) {
            finalSummary = chunkSummaries.get(0);
        } else {
            finalSummary = reducePhase(chunkSummaries);
        }

        // 清空旧记忆，注入摘要，保留近期记忆
        memory.clear();

        // 注入摘要
        MemoryEntry summaryEntry = new MemoryEntry(
                "summary-" + UUID.randomUUID().toString().substring(0, 8),
                "[历史对话摘要] " + finalSummary,
                MemoryEntry.MemoryType.SUMMARY,
                null,
                MemoryEntry.estimateTokens(finalSummary)
        );
        memory.store(summaryEntry);

        // 回注近期记忆
        for (MemoryEntry entry : recentEntries) {
            memory.store(entry);
        }

        log.info("压缩完成：新摘要 {} tokens，保留 {} 条近期记忆",
                summaryEntry.getTokenCount(), recentEntries.size());

        return finalSummary;
    }

    /**
     * Map 阶段：将旧消息分片，每片独立摘要
     */
    private List<String> mapPhase(List<MemoryEntry> oldEntries) {
        List<String> summaries = new ArrayList<>();
        List<List<MemoryEntry>> chunks = partition(oldEntries, chunkSize);

        for (int i = 0; i < chunks.size(); i++) {
            List<MemoryEntry> chunk = chunks.get(i);

            StringBuilder chunkText = new StringBuilder();
            for (MemoryEntry entry : chunk) {
                chunkText.append(entry.getType()).append(": ")
                        .append(entry.getContent()).append("\n\n");
            }

            try {
                String prompt = String.format(MAP_PROMPT, chunkText);

                // 调用 LLM 生成摘要
                String response = chatService.chatText(prompt);
                summaries.add(response);

                log.debug("Map 阶段 - 片段 {}/{} 摘要生成完成", i + 1, chunks.size());

            } catch (Exception e) {
                log.error("Map 阶段 - 片段摘要生成失败", e);

                // 降级：直接截取前 200 字
                String fallback = chunkText.substring(0, Math.min(200, chunkText.length()));
                summaries.add("[压缩] " + fallback);
            }
        }

        return summaries;
    }

    /**
     * Reduce 阶段：合并多个摘要
     */
    private String reducePhase(List<String> summaries) {
        String joined = String.join("\n\n---\n\n", summaries);

        try {
            String prompt = String.format(REDUCE_PROMPT, joined);

            // 调用 LLM 合并摘要
            String response = chatService.chatText(prompt);

            log.debug("Reduce 阶段完成：{} 个摘要合并为 1 个", summaries.size());

            return response;

        } catch (Exception e) {
            log.error("Reduce 阶段 - 摘要合并失败", e);

            // 降级：直接拼接
            return String.join("；", summaries);
        }
    }

    /**
     * 将列表分片
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * 获取配置信息
     */
    public String getConfigSummary() {
        return String.format("压缩器配置: 保留最近 %d 轮，每片 %d 条消息",
                retainRecentRounds, chunkSize);
    }
}
