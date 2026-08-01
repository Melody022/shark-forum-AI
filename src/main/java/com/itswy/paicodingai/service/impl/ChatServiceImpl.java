package com.itswy.paicodingai.service.impl;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.enums.AgentTypeEnum;
import com.itswy.paicodingai.enums.ChatEventTypeEnum;
import com.itswy.paicodingai.service.ChatService;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式对话实现
 *
 * 停止生成原理：
 *   ConcurrentHashMap 存放 sessionId → "true" 表示正在生成
 *   流式输出中 takeWhile 检查这个标记
 *   用户点停止 → remove(sessionId) → takeWhile 检测到标记不存在 → 停止输出
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

    /** 生成状态容器：sessionId → "true" 表示正在生成中 */
    private static final ConcurrentHashMap<String, String> GENERATE_STATUS = new ConcurrentHashMap<>();

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

                // 生成开始时，设置标记
                .doFirst(() -> GENERATE_STATUS.put(sessionId, "true"))

                // 异常时清理标记
                .doOnError(throwable -> GENERATE_STATUS.remove(sessionId))

                // 正常结束时清理标记
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId))

                // 用户取消时，保存已生成的内容（后续接会话记忆时用）
                .doOnCancel(() -> {
                    GENERATE_STATUS.remove(sessionId);
                    log.info("用户取消生成：{}", sessionId);
                })

                // 每次生成一段后检查标记，标记被删除则停止
                .takeWhile(response -> GENERATE_STATUS.get(sessionId) != null)

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
        GENERATE_STATUS.remove(sessionId);
        log.info("停止生成：{}", sessionId);
    }

    @Override
    public String chatText(String question) {
        String agentType = AgentTypeEnum.GENERAL.getAgentName();
        return this.chatClient.prompt()
                .system(this.systemPromptConfig.getSystemMessage(agentType))
                .user(question)
                // 【非流式也支持记忆】
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "text-mode"))
                .call()
                .content();
    }
}
