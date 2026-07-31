package com.itswy.paicodingai.enums;

/**
 * ==========================================================================
 * 消息类型枚举（谁说的这句话）
 * ==========================================================================
 *
 * 聊天记录里，每条消息需要标记是谁发的：
 * - 用户问的 → USER
 * - AI回答的 → ASSISTANT
 *
 * 这个枚举用在 MessageVO（消息体）中，
 * 前端根据 type 把消息显示在左边（用户）还是右边（AI）
 *
 * @date 2026-07-18
 */
public enum MessageTypeEnum {

    /**
     * 用户提问
     * 前端显示在右侧，蓝色气泡
     */
    USER(1, "用户提问"),

    /**
     * AI的回答
     * 前端显示在左侧，白色气泡
     */
    ASSISTANT(2, "AI的回答");

    private final int value;

    private final String desc;

    MessageTypeEnum(int value, String desc) {
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
