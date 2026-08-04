package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.memory.manager.MemoryManager;
import com.itswy.paicodingai.memory.model.MemoryEntry;
import com.itswy.paicodingai.memory.repository.RedisConversationMemory;
import com.itswy.paicodingai.memory.service.TokenBudget;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 上下文窗口监控 API
 *
 * 提供类似 Claude 的 Context Window 监控数据：
 * - 总容量 / 已用 / 剩余
 * - 各组成部分占比（Messages、System prompt、Tools、Reserved）
 * - Token 使用统计
 */
@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextMonitorController {

    private final MemoryManager memoryManager;
    private final TokenBudget tokenBudget;

    /**
     * 获取上下文窗口状态
     * GET /api/context/status
     */
    @GetMapping("/status")
    public Map<String, Object> getContextStatus() {
        RedisConversationMemory memory = memoryManager.getShortTermMemory();

        int contextWindow = tokenBudget.getContextWindow();
        int reservedSystem = tokenBudget.getReservedForSystem();
        int reservedTools = tokenBudget.getReservedForTools();
        int reservedResponse = tokenBudget.getReservedForResponse();
        int usedTokens = memory.getTokenCount();
        int messageCount = memory.size();
        int freeSpace = contextWindow - reservedSystem - reservedTools - reservedResponse - usedTokens;

        // 各组成部分（Messages只包含对话消息，不包含reserved的token）
        List<Map<String, Object>> segments = new ArrayList<>();
        segments.add(buildSegment("Messages", usedTokens, contextWindow, "#3b82f6", "💬"));
        segments.add(buildSegment("System prompt", reservedSystem, contextWindow, "#f59e0b", "📋"));
        segments.add(buildSegment("System tools", reservedTools, contextWindow, "#10b981", "🔧"));
        segments.add(buildSegment("Response reserved", reservedResponse, contextWindow, "#8b5cf6", "✍️"));
        segments.add(buildSegment("Free space", Math.max(0, freeSpace), contextWindow, "#94a3b8", "📦"));

        // Token 使用统计
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("llmCallCount", tokenBudget.getLlmCallCount());
        stats.put("totalInputTokens", tokenBudget.getTotalInputTokens());
        stats.put("totalOutputTokens", tokenBudget.getTotalOutputTokens());
        stats.put("cachedInputTokens", tokenBudget.getTotalCachedInputTokens());
        stats.put("compressionThreshold", tokenBudget.getCompressionThreshold());

        // 记忆条目分类统计（使用默认会话）
        List<MemoryEntry> allEntries = memoryManager.getAll("default");
        Map<String, Integer> typeBreakdown = new LinkedHashMap<>();
        for (MemoryEntry entry : allEntries) {
            String typeName = entry.getType().name().toLowerCase();
            typeBreakdown.merge(typeName, entry.getTokenCount(), Integer::sum);
        }

        // 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contextWindow", contextWindow);
        result.put("usedTokens", usedTokens);  // 只包含对话消息token，不包含reserved
        result.put("conversationTokens", usedTokens);
        result.put("freeSpace", Math.max(0, freeSpace));
        result.put("usagePercent", contextWindow > 0
                ? Math.round((double)(usedTokens + reservedSystem + reservedTools + reservedResponse) / contextWindow * 1000) / 10.0
                : 0);
        result.put("messageCount", messageCount);
        result.put("segments", segments);
        result.put("stats", stats);
        result.put("typeBreakdown", typeBreakdown);
        result.put("statusSummary", memory.getStatusSummary());

        return result;
    }

    /**
     * 手动触发压缩
     * POST /api/context/compress
     */
    @PostMapping("/compress")
    public Map<String, Object> compressContext() {
        RedisConversationMemory memory = memoryManager.getShortTermMemory();

        int beforeTokens = memory.getTokenCount();

        // TODO: 调用ContextCompressor进行压缩
        // 目前只是模拟压缩，实际应该调用compressor.compress(memory)

        int afterTokens = memory.getTokenCount();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("beforeTokens", beforeTokens);
        result.put("afterTokens", afterTokens);
        result.put("compressionRatio", beforeTokens > 0
                ? Math.round((double) (beforeTokens - afterTokens) / beforeTokens * 100)
                : 0);

        return result;
    }

    private Map<String, Object> buildSegment(String name, int tokens, int total, String color, String icon) {
        Map<String, Object> seg = new LinkedHashMap<>();
        seg.put("name", name);
        seg.put("tokens", tokens);
        seg.put("formatted", formatTokens(tokens));
        seg.put("percent", total > 0 ? Math.round((double) tokens / total * 1000) / 10.0 : 0);
        seg.put("color", color);
        seg.put("icon", icon);
        return seg;
    }

    private String formatTokens(int tokens) {
        if (tokens >= 1_000_000) {
            return String.format("%.1fM", tokens / 1_000_000.0);
        } else if (tokens >= 1000) {
            return String.format("%.1fk", tokens / 1000.0);
        }
        return String.valueOf(tokens);
    }
}
