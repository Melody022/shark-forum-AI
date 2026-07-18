package com.itswy.paicodingai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itswy.paicodingai.config.SessionProperties;
import com.itswy.paicodingai.entity.ChatSession;
import com.itswy.paicodingai.mapper.ChatSessionMapper;
import com.itswy.paicodingai.service.ChatSessionService;
import com.itswy.paicodingai.vo.SessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final SessionProperties sessionProperties;

    public ChatSessionServiceImpl(SessionProperties sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    @Override
    public SessionVO createSession(Integer num) {
        if (num == null) num = 3;

        SessionVO sessionVO = new SessionVO();
        sessionVO.setTitle(sessionProperties.getTitle());
        sessionVO.setDescribe(sessionProperties.getDescribe());

        // 生成sessionId
        sessionVO.setSessionId(UUID.randomUUID().toString().replace("-", "").toUpperCase());

        // 随机取examples
        List<String> allExamples = sessionProperties.getExamples();
        List<String> randomExamples = new ArrayList<>();
        if (allExamples != null && !allExamples.isEmpty()) {
            Collections.shuffle(allExamples);
            randomExamples = allExamples.subList(0, Math.min(num, allExamples.size()));
        }
        sessionVO.setExamples(randomExamples);

        // 持久化
        ChatSession chatSession = ChatSession.builder()
                .sessionId(sessionVO.getSessionId())
                .userId(0L)
                .build();
        super.save(chatSession);

        return sessionVO;
    }
}
