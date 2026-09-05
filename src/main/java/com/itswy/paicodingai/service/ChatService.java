package com.itswy.paicodingai.service;

import com.itswy.paicodingai.vo.ChatEventVO;
import reactor.core.publisher.Flux;

import java.util.List;

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

    /** 带用户隔离标识的流式对话。 */
    Flux<ChatEventVO> chat(String question, String sessionId, String userId);

    /** 带图片的多模态对话，图片请求路由到当前多模态 Provider。 */
    Flux<ChatEventVO> chat(String question, String sessionId, String userId, List<String> imageUrls);

    /**
     * 停止 AI 生成
     */
    void stop(String sessionId);

    /**
     * 普通文本对话（非流式）
     */
    String chatText(String question);
}
