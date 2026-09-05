package com.itswy.paicodingai.dto;

import java.util.List;

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

    /** 可选的知识库用户标识；未接入登录系统时由后端回退为 0。 */
    private String userId;

    /** 多模态图片，当前支持浏览器生成的 data URL 或可访问的 HTTPS URL。 */
    private List<String> imageUrls;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
}
