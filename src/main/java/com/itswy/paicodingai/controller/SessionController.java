package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.config.SessionProperties;
import com.itswy.paicodingai.service.ChatSessionService;
import com.itswy.paicodingai.service.impl.ChatSessionServiceImpl;
import com.itswy.paicodingai.vo.ChatSessionVO;
import com.itswy.paicodingai.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ==========================================================================
 * 会话控制器 —— 管理聊天会话
 * ==========================================================================
 *
 * @date 2026-07-18
 */
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final ChatSessionService chatSessionService;
    private final ChatSessionServiceImpl chatSessionServiceImpl;
    private final SessionProperties sessionProperties;

    /**
     * 创建新会话
     * POST /session?n=3
     */
    @PostMapping
    public SessionVO createSession(@RequestParam(value = "n", defaultValue = "3") Integer num) {
        return this.chatSessionService.createSession(num);
    }

    /**
     * 获取示例问题（前端换一换用）
     * GET /session/examples?n=4
     */
    @GetMapping("/examples")
    public Map<String, List<String>> getExamples(@RequestParam(value = "n", defaultValue = "4") Integer num) {
        List<String> all = sessionProperties.getExamples();
        Collections.shuffle(all);
        int count = Math.min(num != null ? num : 4, all.size());
        return Map.of("examples", all.subList(0, count));
    }

    /**
     * 历史会话列表
     * GET /session/history
     */
    @GetMapping("/history")
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        return this.chatSessionService.queryHistorySession();
    }

    /**
     * 删除历史会话
     * DELETE /session/history?sessionId=xxx
     */
    @DeleteMapping("/history")
    public void deleteHistorySession(@RequestParam("sessionId") String sessionId) {
        this.chatSessionService.deleteHistorySession(sessionId);
    }

    /**
     * 修改会话标题
     * PUT /session/history?sessionId=xxx&title=xxx
     */
    @PutMapping("/history")
    public void updateTitle(@RequestParam("sessionId") String sessionId,
                            @RequestParam("title") String title) {
        this.chatSessionService.updateTitle(sessionId, title);
    }

    /**
     * 查询会话的历史消息列表
     * GET /session/history/messages?sessionId=xxx
     *
     * 返回格式：
     * {
     *   "messages": [
     *     {"role": "user", "content": "Java怎么学？"},
     *     {"role": "assistant", "content": "Java学习路线..."}
     *   ],
     *   "count": 2
     * }
     */
    @GetMapping("/history/messages")
    public Map<String, Object> getConversationHistory(
            @RequestParam("sessionId") String sessionId) {

        List<Message> messages = chatSessionServiceImpl.getConversationHistory(sessionId);

        // 将 Message 对象转换为简单的 JSON 格式
        List<Map<String, String>> messageList = messages.stream()
                .map(msg -> Map.of(
                    "role", msg.getMessageType().name().toLowerCase(),
                    "content", msg.getText()
                ))
                .collect(Collectors.toList());

        return Map.of(
            "messages", messageList,
            "count", messageList.size()
        );
    }

    /**
     * 清除会话的历史消息
     * DELETE /session/history/messages?sessionId=xxx
     */
    @DeleteMapping("/history/messages")
    public void clearConversationHistory(@RequestParam("sessionId") String sessionId) {
        chatSessionServiceImpl.clearConversationHistory(sessionId);
    }
}
