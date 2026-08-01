package com.itswy.paicodingai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itswy.paicodingai.config.SessionProperties;
import com.itswy.paicodingai.entity.ChatSession;
import com.itswy.paicodingai.mapper.ChatSessionMapper;
import com.itswy.paicodingai.service.ChatSessionService;
import com.itswy.paicodingai.vo.ChatSessionVO;
import com.itswy.paicodingai.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ==========================================================================
 * 会话管理服务实现
 * ==========================================================================
 *
 * 第一期：createSession + queryBySessionId + queryHistorySession
 * 第二期：加 update + chatMemory 查询对话详情 ✅ 已实现
 *
 * @date 2026-07-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final SessionProperties sessionProperties;
    private final ChatMemoryRepository chatMemoryRepository;

    /**
     * 创建新会话
     *
     * 三件事：随机示例 → 生成 UUID → 存数据库
     */
    @Override
    public SessionVO createSession(Integer num) {
        SessionVO sessionVO = new SessionVO();
        sessionVO.setTitle(sessionProperties.getTitle());
        sessionVO.setDescribe(sessionProperties.getDescribe());

        // 随机选取示例问题
        List<String> allExamples = sessionProperties.getExamples();
        if (allExamples != null && !allExamples.isEmpty()) {
            int count = Math.min(num != null ? num : 3, allExamples.size());
            sessionVO.setExamples(RandomUtil.randomEleList(allExamples, count));
        }

        // 生成 UUID 作为 sessionId
        sessionVO.setSessionId(IdUtil.simpleUUID());

        // 保存到数据库
        ChatSession chatSession = ChatSession.builder()
                .sessionId(sessionVO.getSessionId())
                .userId(0L)
                .build();
        super.save(chatSession);

        return sessionVO;
    }

    /**
     * 更新会话信息
     */
    @Override
    public void update(String sessionId, String title, Long userId) {
        var chatSessionList = super.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .list();

        if (CollUtil.isEmpty(chatSessionList)) return;

        var chatSession = chatSessionList.get(0);
        if (StrUtil.isEmpty(chatSession.getTitle()) && StrUtil.isNotEmpty(title)) {
            chatSession.setTitle(StrUtil.sub(title, 0, 100));
        }
        chatSession.setUpdateTime(LocalDateTime.now());
        super.updateById(chatSession);
    }

    /**
     * 查询历史会话列表（按时间分组）
     */
    @Override
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        var chatSessionList = super.lambdaQuery()
                .eq(ChatSession::getUserId, 0L)
                .isNotNull(ChatSession::getTitle)
                .orderByDesc(ChatSession::getUpdateTime)
                .last("LIMIT 30")
                .list();

        if (CollUtil.isEmpty(chatSessionList)) return Map.of();

        var chatSessionVOList = chatSessionList.stream()
                .map(s -> new ChatSessionVO(s.getSessionId(), s.getTitle(), s.getUpdateTime()))
                .collect(Collectors.toList());

        var now = LocalDateTime.now().toLocalDate();
        return chatSessionVOList.stream()
                .collect(Collectors.groupingBy(vo -> {
                    var days = Math.abs(ChronoUnit.DAYS.between(vo.getUpdateTime().toLocalDate(), now));
                    if (days == 0) return "当天";
                    else if (days <= 30) return "最近30天";
                    else if (days <= 365) return "最近1年";
                    else return "1年以上";
                }));
    }

    @Override
    public void deleteHistorySession(String sessionId) {
        // 删除数据库记录
        super.remove(com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, 0L));

        // 同时删除 Redis 中的对话记忆
        chatMemoryRepository.deleteByConversationId(sessionId);
        log.info("已删除会话及历史记忆: {}", sessionId);
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        super.lambdaUpdate()
                .set(ChatSession::getTitle, StrUtil.sub(title, 0, 100))
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, 0L)
                .update();
    }

    /**
     * 查询会话的历史消息列表（从 Redis 读取）
     */
    public List<Message> getConversationHistory(String sessionId) {
        return chatMemoryRepository.findByConversationId(sessionId);
    }

    /**
     * 清除会话的历史消息
     */
    public void clearConversationHistory(String sessionId) {
        chatMemoryRepository.deleteByConversationId(sessionId);
        log.info("已清除会话历史: {}", sessionId);
    }
}
