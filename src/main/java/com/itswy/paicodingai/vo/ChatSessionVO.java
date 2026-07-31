package com.itswy.paicodingai.vo;

import java.time.LocalDateTime;

/**
 * ==========================================================================
 * 历史会话 VO —— 历史会话列表中的一条记录
 * ==========================================================================
 *
 * 聊天窗口侧边栏会显示历史会话列表，
 * 每条历史会话包含：
 *   - sessionId：会话ID，点击后加载该会话的聊天记录
 *   - title：会话标题（自动根据第一句话生成）
 *   - updateTime：最后更新时间，用于排序
 *
 * @date 2026-07-18
 */
public class ChatSessionVO {

    /** 会话ID */
    private String sessionId;

    /** 会话标题（比如"Java怎么学"） */
    private String title;

    /** 最后更新时间 */
    private LocalDateTime updateTime;

    public ChatSessionVO() {}

    public ChatSessionVO(String sessionId, String title, LocalDateTime updateTime) {
        this.sessionId = sessionId;
        this.title = title;
        this.updateTime = updateTime;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
