package com.itswy.paicodingai.enums;

/**
 * ==========================================================================
 * 聊天消息事件类型枚举
 * ==========================================================================
 *
 * 这个枚举定义了 SSE（Server-Sent Events）流式返回时，
 * 每一帧数据的事件类型。
 *
 * 前端收到的数据长这样：
 *   {"eventType": 1001, "eventData": "你好"}     ← AI正在说话
 *   {"eventType": 1001, "eventData": "，我是"}   ← AI继续说话
 *   {"eventType": 1002, "eventData": null}       ← AI说完了
 *   {"eventType": 1003, "eventData": {...}}      ← AI调用了工具（后面阶段用）
 *
 * 为什么要有事件类型？
 *   因为流式返回的数据不止一种：
 *   - AI生成文本时 → 前端把文字追加到聊天框
 *   - AI说完时 → 前端停止"正在输入..."状态
 *   - AI调用工具查询数据时 → 前端显示一个商品卡片
 *   前端根据 eventType 来判断该怎么渲染
 *
 * @date 2026-07-18
 */
public enum ChatEventTypeEnum {

    /**
     * 数据事件（值：1001）
     * AI正在生成回复内容，eventData 就是生成的文本片段
     * 前端收到后 → 追加到聊天气泡中（打字机效果）
     */
    DATA(1001, "数据事件"),

    /**
     * 停止事件（值：1002）
     * AI回复结束，不再有新数据
     * 前端收到后 → 关闭"正在输入..."的loading状态
     */
    STOP(1002, "停止事件"),

    /**
     * 参数事件（值：1003）
     * AI调用了工具，返回工具的执行结果（如查询到的课程信息）
     * 前端收到后 → 展示一个卡片/按钮（后面阶段用到）
     */
    PARAM(1003, "参数事件");

    /**
     * 事件值，传给前端用
     */
    private final int value;

    /**
     * 描述，给我们开发者看
     */
    private final String desc;

    ChatEventTypeEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
