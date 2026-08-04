package com.itswy.paicodingai.service.impl;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.enums.AgentTypeEnum;
import com.itswy.paicodingai.enums.ChatEventTypeEnum;
import com.itswy.paicodingai.memory.util.RedisUtils;
import com.itswy.paicodingai.service.ChatService;
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
 * 流式对话实现
 *
 * 停止生成原理：
 *   Redis 存放 sessionId → "true" 表示正在生成
 *   流式输出中 takeWhile 检查这个标记
 *   用户点停止 → 删除标记 → takeWhile 检测到标记不存在 → 停止输出
 *
 * Tool Calling原理：
 *   1. ChatClient注册了ArticleTools和CourseTools
 *   2. LLM决定调用哪个工具，Spring AI自动执行
 *   3. 工具结果存储到ToolResultHolder
 *   4. 流式输出结束后，返回PARAM事件（包含工具调用结果）
 *   5. 前端接收PARAM事件，根据JSON数据渲染成卡片
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
        var requestId = generateRequestId();

        return this.chatClient.prompt()
                .system(this.systemPromptConfig.getSystemMessage(agentType))
                .user(question)
                // 【关键改进】传入 conversationId，让 ChatMemory 自动管理历史消息
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                // 【Tool Calling】传递requestId到工具上下文
                .toolContext(Map.of("requestId", requestId))
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

                    // 检查是否有工具调用
                    var finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                    if ("stop".equals(finishReason)) {
                        // 将消息ID与请求ID关联，用于后续获取工具调用结果
                        var messageId = chatResponse.getMetadata().getId();
                        ToolResultHolder.put(messageId, "requestId", requestId);
                        log.debug("工具调用完成: messageId={}, requestId={}", messageId, requestId);
                    }

                    return ChatEventVO.data(text);  // ★ 使用静态工厂方法创建DATA事件
                })

                // ★ 关键：流式输出结束后，返回工具调用结果（PARAM事件）
                .concatWith(Flux.defer(() -> {
                    var result = ToolResultHolder.get(requestId);
                    if (result != null && !result.isEmpty()) {
                        // 有工具调用结果，返回PARAM事件
                        ToolResultHolder.remove(requestId);
                        log.info("返回工具调用结果: requestId={}, keys={}", requestId, result.keySet());

                        return Flux.just(
                                ChatEventVO.param(result),  // ★ 使用静态工厂方法
                                STOP_EVENT
                        );
                    }
                    // 没有工具调用结果，直接返回STOP
                    return Flux.just(STOP_EVENT);
                }));
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
