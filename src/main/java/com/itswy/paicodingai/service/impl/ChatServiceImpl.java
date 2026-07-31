package com.itswy.paicodingai.service.impl;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.enums.AgentTypeEnum;
import com.itswy.paicodingai.enums.ChatEventTypeEnum;
import com.itswy.paicodingai.service.ChatService;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * ==========================================================================
 * 流式对话实现（第一期核心！）
 * ==========================================================================
 *
 * 核心数据流：
 *
 *   前端发来 {question, sessionId}
 *       ↓
 *   SystemPromptConfig.getSystemMessage(agentType)
 *     → 读取 base.md + agents/xxx.md 拼装完整提示词
 *       ↓
 *   chatClient.prompt()
 *       .system(systemMessage)        ← 拼装好的提示词
 *       .user(question)               ← 用户的问题
 *       .stream().chatResponse()      ← 流式调用大模型
 *       ↓
 *   Flux<ChatEventVO>                  ← 每生成一段文字，发一个事件
 *       ↓
 *   前端以 SSE 接收，实现打字机效果
 *
 * @date 2026-07-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** SSE 结束事件常量（type=1002，前端收到后关闭 loading） */
    public static final ChatEventVO STOP_EVENT = new ChatEventVO(null, ChatEventTypeEnum.STOP.getValue());

    /** Spring AI 的聊天客户端（在 SpringAIConfig 中配置） */
    private final ChatClient chatClient;

    /** 系统提示词配置（从 prompts/ 目录的 Markdown 文件读取） */
    private final SystemPromptConfig systemPromptConfig;

    /**
     * 流式对话
     *
     * @param question  用户的问题
     * @param sessionId 会话ID
     * @return 流式事件
     */
    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        log.info("用户提问：{}，会话：{}", question, sessionId);

        // 现在是单智能体阶段，固定用 GENERAL 类型
        // 后续多智能体阶段，这里会改成：先调 RouteAgent 分析意图，再用对应的 AgentType
        String agentType = AgentTypeEnum.GENERAL.getAgentName();

        return this.chatClient.prompt()

                // ---- system prompt：从 prompts/ 目录读取拼装 ----
                .system(this.systemPromptConfig.getSystemMessage(agentType))

                // ---- user prompt：用户当前的提问 ----
                .user(question)

                // ---- 流式调用 ----
                .stream()
                .chatResponse()

                // ---- map：把 AI 每段回复转成 ChatEventVO ----
                .map(chatResponse -> {
                    var text = chatResponse.getResult().getOutput().getText();
                    return new ChatEventVO(text, ChatEventTypeEnum.DATA.getValue());
                })

                // ---- 追加结束标记 ----
                .concatWith(Flux.just(STOP_EVENT));
    }

    /**
     * 普通文本对话（非流式，一次性返回完整结果）
     */
    @Override
    public String chatText(String question) {
        String agentType = AgentTypeEnum.GENERAL.getAgentName();

        return this.chatClient.prompt()
                .system(this.systemPromptConfig.getSystemMessage(agentType))
                .user(question)
                .call()
                .content();
    }
}
