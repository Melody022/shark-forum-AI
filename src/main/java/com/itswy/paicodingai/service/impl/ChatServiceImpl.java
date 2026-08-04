package com.itswy.paicodingai.service.impl;

import com.itswy.paicodingai.agent.AgentContext;
import com.itswy.paicodingai.agent.RouteAgent;
import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.enums.AgentTypeEnum;
import com.itswy.paicodingai.enums.ChatEventTypeEnum;
import com.itswy.paicodingai.memory.util.RedisUtils;
import com.itswy.paicodingai.service.ChatService;
import com.itswy.paicodingai.service.ChatSessionService;
import com.itswy.paicodingai.tools.ToolResultHolder;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 流式对话实现（支持多Agent路由）
 *
 * 参考天机学堂实现：
 * 1. 用户提问 → RouteAgent识别意图
 * 2. 路由到对应Agent（ArticleAgent/CourseAgent/KnowledgeAgent/GeneralAgent）
 * 3. Agent调用工具，返回结果
 *
 * 停止生成原理：
 *   Redis 存放 sessionId → "true" 表示正在生成
 *   流式输出中 takeWhile 检查这个标记
 *   用户点停止 → 删除标记 → takeWhile 检测到标记不存在 → 停止输出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final ChatEventVO STOP_EVENT = new ChatEventVO(null, ChatEventTypeEnum.STOP.getValue());

    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final RedisUtils redisUtils;
    private final RouteAgent routeAgent;
    private final ChatSessionService chatSessionService;

    /** 生成状态的Redis Key前缀 */
    private static final String GENERATE_STATUS_KEY = "chat:generate:status:";

    /** 状态保留时长（秒），防止异常时状态残留 */
    private static final long STATUS_EXPIRE_SECONDS = 300; // 5分钟

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        log.info("用户提问：{}，会话：{}", question, sessionId);

        var requestId = generateRequestId();
        AgentContext ctx = AgentContext.builder()
            .sessionId(sessionId)
            .requestId(requestId)
            .build();

        // 更新会话标题（取问题前20个字符）
        String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
        chatSessionService.update(sessionId, title, 0L);

        return routeAgent.route(question, ctx)
            // 生成开始时，在Redis中设置标记
            .doFirst(() -> {
                String key = GENERATE_STATUS_KEY + sessionId;
                redisUtils.opsForValue().set(key, "true", STATUS_EXPIRE_SECONDS, TimeUnit.SECONDS);
                log.debug("设置生成状态: sessionId={}", sessionId);
            })
            // 异常时清理标记
            .doOnError(throwable -> {
                clearGenerateStatus(sessionId);
                log.error("生成异常，清除状态: sessionId={}", sessionId, throwable);
            })
            // 正常结束时清理标记
            .doOnComplete(() -> {
                clearGenerateStatus(sessionId);
                log.debug("生成完成，清除状态: sessionId={}", sessionId);
            })
            // 用户取消时，保存已生成的内容
            .doOnCancel(() -> {
                clearGenerateStatus(sessionId);
                log.info("用户取消生成：{}", sessionId);
            })
            // 每次生成一段后检查标记，标记被删除则停止
            .takeWhile(response -> {
                String key = GENERATE_STATUS_KEY + sessionId;
                String status = redisUtils.opsForValue().get(key);
                return status != null;
            })
            // 添加STOP事件
            .concatWith(Flux.just(STOP_EVENT));
    }

    @Override
    public void stop(String sessionId) {
        clearGenerateStatus(sessionId);
        log.info("停止生成：{}", sessionId);
    }

    @Override
    public String chatText(String question) {
        String agentType = AgentTypeEnum.GENERAL.getAgentName();
        return this.chatClient.prompt()
                .system(this.systemPromptConfig.getSystemMessage(agentType))
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "text-mode"))
                .call()
                .content();
    }

    /**
     * 清除生成状态
     */
    private void clearGenerateStatus(String sessionId) {
        String key = GENERATE_STATUS_KEY + sessionId;
        redisUtils.delete(key);
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
