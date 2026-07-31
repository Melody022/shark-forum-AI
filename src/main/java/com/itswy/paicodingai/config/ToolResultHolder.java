package com.itswy.paicodingai.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ==========================================================================
 * 工具结果保持器
 * ==========================================================================
 *
 * 用途：存放 Tool 被调用后返回的结果数据。
 *
 * 工作流程：
 *   1. AI 调用某个工具（CourseTools.queryArticleById）
 *   2. 工具执行完毕，把结果放入 ToolResultHolder
 *   3. AI 回复结束后，ChatServiceImpl 从这里取出结果
 *   4. 包装成 PARAM 事件（type=1003）返回给前端
 *   5. 前端根据结果展示卡片（如文章信息卡片）
 *
 * 用 requestId 作为 key 的原因：
 *   同一个会话中可能并发多个请求，requestId 用来区分"是哪次请求的结果"。
 *
 * 第三阶段（Tool Calling）才会真正用到这个类，
 * 第一、二阶段先占好位。
 *
 * @date 2026-07-18
 */
public class ToolResultHolder {

    /** requestId → { field → result } */
    private static final Map<String, Map<String, Object>> HANDLER_MAP = new ConcurrentHashMap<>();

    /**
     * 存储结果
     * @param requestId  请求ID
     * @param field      数据字段名（如 "articleInfo_123"）
     * @param result     结果数据
     */
    public static void put(String requestId, String field, Object result) {
        HANDLER_MAP.computeIfAbsent(requestId, k -> new HashMap<>()).put(field, result);
    }

    /**
     * 获取某次请求的所有结果
     */
    public static Map<String, Object> get(String requestId) {
        return requestId == null ? null : HANDLER_MAP.get(requestId);
    }

    /**
     * 获取某次请求的某个字段结果
     */
    public static Object get(String requestId, String field) {
        return Optional.ofNullable(HANDLER_MAP.get(requestId))
                .map(map -> map.get(field))
                .orElse(null);
    }

    /**
     * 移除某次请求的所有结果（用完即删，防止内存泄漏）
     */
    public static void remove(String requestId) {
        HANDLER_MAP.remove(requestId);
    }
}
