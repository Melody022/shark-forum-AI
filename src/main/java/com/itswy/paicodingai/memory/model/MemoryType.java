package com.itswy.paicodingai.memory.model;

/**
 * 记忆类型枚举
 *
 * 定义四种记忆类型：
 * - CONVERSATION：普通对话消息
 * - FACT：事实记忆（用户偏好、项目配置等）
 * - SUMMARY：压缩后的历史摘要
 * - TOOL_RESULT：工具调用返回结果
 */
public enum MemoryType {
    CONVERSATION,
    FACT,
    SUMMARY,
    TOOL_RESULT
}
