package com.itswy.paicodingai.dto;

/**
 * ==========================================================================
 * 聊天请求参数 —— 前端发来的消息
 * ==========================================================================
 *
 * 前端 POST /ai/chat 时传的 JSON 体：
 * {
 *     "question": "Java怎么学？",
 *     "sessionId": "abc-def-ghi"
 * }
 *
 * 这个类就是用来接收这个 JSON 的。
 *
 * @date 2026-07-18
 */
public class ChatDTO {

    /**
     * 用户的问题文本
     * 比如："Java怎么学？"、"Spring Boot是什么？"
     */
    private String question;

    /**
     * 会话ID
     * 前端在创建会话时拿到 sessionId，
     * 之后聊天一直带着它，表示"我在这个对话里说话"
     */
    private String sessionId;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
