package com.itswy.paicodingai.service.impl;

import com.itswy.paicodingai.agent.AgentContext;
import com.itswy.paicodingai.agent.PaicodingAgent;
import com.itswy.paicodingai.config.AuthContext;
import com.itswy.paicodingai.enums.ChatEventTypeEnum;
import com.itswy.paicodingai.memory.util.RedisUtils;
import com.itswy.paicodingai.service.ChatService;
import com.itswy.paicodingai.service.ChatSessionService;
import com.itswy.paicodingai.service.prompt.PromptAssembler;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 流式对话实现
 *
 * 使用PaicodingAgent处理用户请求
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final ChatEventVO STOP_EVENT = new ChatEventVO(null, ChatEventTypeEnum.STOP.getValue());

    private final ChatClient chatClient;
    private final PromptAssembler promptAssembler;
    private final RedisUtils redisUtils;
    private final PaicodingAgent paicodingAgent;
    private final ChatSessionService chatSessionService;

    /** 生成状态的Redis Key前缀 */
    private static final String GENERATE_STATUS_KEY = "chat:generate:status:";

    /** 状态保留时长（秒），防止异常时状态残留 */
    private static final long STATUS_EXPIRE_SECONDS = 300; // 5分钟

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        return chat(question, sessionId, "0");
    }

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId, String userId) {
        return chat(question, sessionId, userId, List.of());
    }

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId, String userId, List<String> imageUrls) {
        log.info("用户提问：{}，会话：{}", question, sessionId);

        var requestId = generateRequestId();
        // 身份:已登录走 AuthContext,否则沿用入参(旧前端固定 '0');角色决定工具白名单
        String resolvedUser = AuthContext.get() != null
                ? AuthContext.currentUserId()
                : (userId == null || userId.isBlank() ? "0" : userId);
        AgentContext ctx = AgentContext.builder()
            .sessionId(sessionId)
            .requestId(requestId)
            .userId(resolvedUser)
            .roleCode(AuthContext.currentRole())
            .build();

        // 更新会话标题（取问题前20个字符）
        String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
        chatSessionService.update(sessionId, title, 0L);

        Flux<ChatEventVO> responseFlux = imageUrls == null || imageUrls.isEmpty()
                ? paicodingAgent.chat(question, ctx)
                : paicodingAgent.chat(question, ctx, imageUrls);

        return responseFlux
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
            .concatWith(Flux.just(STOP_EVENT))
            // SSE 错误兜底:把真实异常转成可读文本事件,避免裸异常冒泡到 /error 导致 HttpMessageNotWritableException 掩盖原因
            .onErrorResume(throwable -> {
                clearGenerateStatus(sessionId);
                String msg = throwable.getMessage() == null
                        ? throwable.getClass().getSimpleName()
                        : throwable.getMessage();
                if (msg.length() > 200) {
                    msg = msg.substring(0, 200) + "…";
                }
                return Flux.just(ChatEventVO.data("抱歉，服务出错了：" + msg), STOP_EVENT);
            });
    }

    @Override
    public void stop(String sessionId) {
        clearGenerateStatus(sessionId);
        log.info("停止生成：{}", sessionId);
    }

    @Override
    public String chatText(String question) {
        return this.chatClient.prompt()
                .system(this.promptAssembler.assembleMain())
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
