package com.itswy.paicodingai.service;

import com.itswy.paicodingai.vo.ChatEventVO;
import reactor.core.publisher.Flux;

/**
 * 聊天服务接口
 */
public interface ChatService {

    static String getConversationId(String sessionId) {
        return sessionId;
    }

    /**
     * 流式对话
     */
    Flux<ChatEventVO> chat(String question, String sessionId);

    /**
     * 停止 AI 生成
     */
    void stop(String sessionId);

    /**
     * 普通文本对话（非流式）
     */
    String chatText(String question);
}
