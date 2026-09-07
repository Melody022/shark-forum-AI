package com.itswy.paicodingai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * ==========================================================================
 * 聊天请求参数 —— 前端发来的消息
 * ==========================================================================
 */
@Schema(description = "聊天(SSE 流式)请求体")
public class ChatDTO {

    @Schema(description = "用户问题/消息文本", example = "能帮我推荐一个合适的教程吗？", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Schema(description = "会话 ID。首次可空(后端按当前登录用户自动建会话)或填已有会话继续对话", example = "072f2a276fe446ca8daf955418a41881")
    private String sessionId;

    @Schema(description = "用户 ID;登录后由后端从 token 取,通常无需传(可留空)", defaultValue = "0", example = "1")
    private String userId;

    @Schema(description = "多模态图片:base64 data URL 或 HTTPS URL,最多 4 张,每张 ≤8MB;文本问答可不传",
            example = "[\"data:image/png;base64,iVBORw0KGgo=\"]")
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
