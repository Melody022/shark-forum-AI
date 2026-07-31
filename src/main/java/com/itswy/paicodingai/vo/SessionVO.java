package com.itswy.paicodingai.vo;

import java.util.List;

/**
 * ==========================================================================
 * 会话 VO —— 创建新会话时返回给前端的数据
 * ==========================================================================
 *
 * 前端打开聊天窗口时调用 POST /ai/session，
 * 后端返回这个对象，包含了：
 *   1. sessionId —— 本次会话的唯一ID（后面聊天都要带上）
 *   2. title —— AI助手的名称
 *   3. describe —— AI助手的介绍
 *   4. examples —— 示例问题（让用户知道可以问什么）
 *
 * 前端拿到这些数据后：
 *   - 显示"HELLO, 我是AI助手"的欢迎页面
 *   - 展示示例问题按钮
 *   - 保存 sessionId，后续聊天请求都带着它
 *
 * @date 2026-07-18
 */
public class SessionVO {

    /**
     * 会话ID（UUID字符串）
     * 每次创建会话都会生成一个新的
     */
    private String sessionId;

    /**
     * AI助手的标题
     * 比如："HELLO, 我是AI助手"
     */
    private String title;

    /**
     * AI助手的简要描述
     * 比如："我是技术派论坛的智能助理，我能推荐教程、答疑解惑..."
     */
    private String describe;

    /**
     * 示例问题列表
     * 比如：["能帮我推荐一个教程？", "Java和Python有什么区别？"]
     * 前端展示为快捷按钮，用户点击就直接发送这个问题
     */
    private List<String> examples;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }
}
