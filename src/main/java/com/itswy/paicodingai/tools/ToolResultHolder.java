package com.itswy.paicodingai.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具结果保持器
 *
 * 用于存储Tool Calling的执行结果，供前端获取
 * Key: requestId
 * Value: Map<fieldName, result>
 *
 * 与天机学堂的设计保持一致
 */
public class ToolResultHolder {

    /**
     * 存储结构：requestId → {fieldName → result}
     */
    private static final Map<String, Map<String, Object>> HANDLER_MAP = new ConcurrentHashMap<>();

    /**
     * 存储工具调用结果
     *
     * @param requestId 请求ID
     * @param field 字段名（如：articleInfo_123）
     * @param result 结果对象
     */
    public static void put(String requestId, String field, Object result) {
        if (requestId == null || field == null) {
            return;
        }
        HANDLER_MAP.computeIfAbsent(requestId, k -> new HashMap<>()).put(field, result);
    }

    /**
     * 获取所有工具调用结果
     *
     * @param requestId 请求ID
     * @return 结果Map
     */
    public static Map<String, Object> get(String requestId) {
        return requestId == null ? null : HANDLER_MAP.get(requestId);
    }

    /**
     * 获取指定字段的结果
     *
     * @param requestId 请求ID
     * @param field 字段名
     * @return 结果对象
     */
    public static Object get(String requestId, String field) {
        if (requestId == null || field == null) {
            return null;
        }
        Map<String, Object> map = HANDLER_MAP.get(requestId);
        return map == null ? null : map.get(field);
    }

    /**
     * 删除指定requestId的所有结果
     *
     * @param requestId 请求ID
     */
    public static void remove(String requestId) {
        if (requestId != null) {
            HANDLER_MAP.remove(requestId);
        }
    }

    /**
     * 检查是否有工具调用结果
     *
     * @param requestId 请求ID
     * @return 是否有结果
     */
    public static boolean hasResult(String requestId) {
        Map<String, Object> map = HANDLER_MAP.get(requestId);
        return map != null && !map.isEmpty();
    }
}
