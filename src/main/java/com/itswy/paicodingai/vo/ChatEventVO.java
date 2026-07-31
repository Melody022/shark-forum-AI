package com.itswy.paicodingai.vo;

import com.itswy.paicodingai.enums.ChatEventTypeEnum;

/**
 * ==========================================================================
 * 聊天事件 VO —— SSE 流式返回的每一帧数据
 * ==========================================================================
 *
 * 这是整个项目最核心的 VO！前端打字机效果就靠它。
 *
 * SSE（Server-Sent Events）的工作方式：
 *   服务端把数据切成一小块一小块，不断推给前端。
 *   每一小块就是一个 ChatEventVO。
 *
 * 前端收到的数据流长这样（每一行是一个 ChatEventVO 的 JSON）：
 *   data: {"eventType": 1001, "eventData": "你"}
 *   data: {"eventType": 1001, "eventData": "好"}
 *   data: {"eventType": 1001, "eventData": "！"}
 *   data: {"eventType": 1002, "eventData": null}    ← AI说完了
 *
 * 事件类型（eventType）：
 *   1001 → DATA：    AI正在回复，eventData是文本片段
 *   1002 → STOP：    AI回复完毕，前端停止loading
 *   1003 → PARAM：   AI调用了工具，eventData是工具返回的数据（后面阶段用）
 *
 * @date 2026-07-18
 */
public class ChatEventVO {

    /**
     * 事件数据
     * 类型是 Object，因为不同类型的数据格式不一样：
     * - DATA事件：String（文本片段）
     * - STOP事件：null
     * - PARAM事件：Map（工具返回的键值对）
     */
    private Object eventData;

    /**
     * 事件类型
     * @see ChatEventTypeEnum
     * 1001 = DATA = AI在说话
     * 1002 = STOP = AI说完了
     * 1003 = PARAM = 返回工具调用结果
     */
    private int eventType;

    public ChatEventVO() {}

    public ChatEventVO(Object eventData, int eventType) {
        this.eventData = eventData;
        this.eventType = eventType;
    }

    public Object getEventData() { return eventData; }
    public void setEventData(Object eventData) { this.eventData = eventData; }
    public int getEventType() { return eventType; }
    public void setEventType(int eventType) { this.eventType = eventType; }

    /**
     * 快速创建一个 DATA 事件（AI在说话）
     * @param data 文本片段
     */
    public static ChatEventVO data(Object data) {
        return new ChatEventVO(data, ChatEventTypeEnum.DATA.getValue());
    }

    /**
     * 快速创建一个 STOP 事件（AI说完了）
     */
    public static ChatEventVO stop() {
        return new ChatEventVO(null, ChatEventTypeEnum.STOP.getValue());
    }
}
