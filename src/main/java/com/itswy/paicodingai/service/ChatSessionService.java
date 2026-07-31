package com.itswy.paicodingai.service;

import com.itswy.paicodingai.vo.ChatSessionVO;
import com.itswy.paicodingai.vo.SessionVO;

import java.util.List;
import java.util.Map;

/**
 * ==========================================================================
 * 会话管理服务接口
 * ==========================================================================
 *
 * @date 2026-07-18
 */
public interface ChatSessionService {

    /**
     * 创建新会话
     */
    SessionVO createSession(Integer num);

    /**
     * 更新会话
     */
    void update(String sessionId, String title, Long userId);

    /**
     * 查询历史会话列表
     */
    Map<String, List<ChatSessionVO>> queryHistorySession();

    /**
     * 删除历史会话
     */
    void deleteHistorySession(String sessionId);

    /**
     * 修改会话标题
     */
    void updateTitle(String sessionId, String title);
}
