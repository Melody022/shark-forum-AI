package com.itswy.paicodingai.service.impl;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.enums.AgentTypeEnum;
import com.itswy.paicodingai.enums.ChatEventTypeEnum;
import com.itswy.paicodingai.memory.util.RedisUtils;
import com.itswy.paicodingai.service.ChatService;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

/**
 * 流式对话实现
 *
 * 停止生成原理：
 *   Redis 存放 sessionId → "true" 表示正在生成
 *   流式输出中 takeWhile 检查这个标记
 *   用户点停止 → 删除标记 → takeWhile 检测到标记不存在 → 停止输出
 *
 * 为什么用Redis而不是ConcurrentHashMap：
 *   1. 多实例部署时状态可共享
 *   2. 支持TTL自动清理（防止内存泄漏）
 *   3. 与天机学堂的实现保持一致
 *
 * 第二期改进：
 *   通过 ChatMemory 实现对话记忆
 *   每次请求时传入 conversationId（sessionId）
 *   Spring AI 自动加载历史消息并注入到 context 中
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final ChatEventVO STOP_EVENT = new ChatEventVO(null, ChatEventTypeEnum.STOP.getValue());

    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final RedisUtils redisUtils;

    /** 生成状态的Redis Key前缀 */
    private static final String GENERATE_STATUS_KEY = "chat:generate:status:";

    /** 状态保留时长（秒），防止异常时状态残留 */
    private static final long STATUS_EXPIRE_SECONDS = 300; // 5分钟

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        log.info("用户提问：{}，会话：{}", question, sessionId);

        String agentType = AgentTypeEnum.GENERAL.getAgentName();
        var outputBuilder = new StringBuilder();

        return this.chatClient.prompt()
                .system(this.systemPromptConfig.getSystemMessage(agentType))
                .user(question)
                // 【关键改进】传入 conversationId，让 ChatMemory 自动管理历史消息
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse()

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

                // 把 AI 每段回复转成 ChatEventVO
                .map(chatResponse -> {
                    var text = chatResponse.getResult().getOutput().getText();
                    outputBuilder.append(text);
                    return new ChatEventVO(text, ChatEventTypeEnum.DATA.getValue());
                })

                // 追加结束标记
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
}
