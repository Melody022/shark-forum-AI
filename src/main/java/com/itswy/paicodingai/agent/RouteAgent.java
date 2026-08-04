package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 路由Agent
 *
 * 负责识别用户意图，路由到对应的Agent
 * 参考天机学堂实现：使用LLM进行意图识别
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteAgent {

    private final ChatClient chatClient;
    private final List<Agent> agents;

    private static final String ROUTE_PROMPT = """
你是一个智能路由助手，负责识别用户意图并返回对应的Agent名称。

可用的Agent：
1. ArticleAgent - 处理文章相关查询（查询文章、搜索文章、热门文章、文章列表）
2. CourseAgent - 处理教程课程相关查询（查询教程、搜索教程、推荐教程、教程列表）
3. KnowledgeAgent - 处理知识库问答（技术问题、知识性问题、概念解释、原理讲解）
4. GeneralAgent - 处理通用对话（问候、闲聊、其他无法分类的问题）

用户问题：{question}

请以JSON格式返回，只返回JSON，不要返回其他内容：
{
  "targetAgent": "Agent名称",
  "reason": "识别原因"
}
""";

    /**
     * 路由到对应的Agent
     *
     * @param question 用户问题
     * @param ctx Agent上下文
     * @return 流式响应
     */
    public Flux<ChatEventVO> route(String question, AgentContext ctx) {
        log.info("路由Agent开始处理: question={}", question);

        // 1. LLM识别意图
        IntentResult intent = recognizeIntent(question, ctx.getSessionId());
        log.info("意图识别结果: targetAgent={}, reason={}", intent.targetAgent, intent.reason);

        // 2. 发送路由事件
        ChatEventVO routeEvent = ChatEventVO.route(intent.targetAgent, intent.reason);

        // 3. 获取目标Agent
        Agent targetAgent = findAgent(intent.targetAgent);
        if (targetAgent == null) {
            log.warn("未找到Agent: {}，使用GeneralAgent", intent.targetAgent);
            targetAgent = findAgent("GeneralAgent");
        }

        Agent finalAgent = targetAgent;
        return Flux.just(routeEvent)
                   .concatWith(finalAgent.chat(question, ctx));
    }

    /**
     * LLM意图识别
     */
    private IntentResult recognizeIntent(String question, String sessionId) {
        String prompt = ROUTE_PROMPT.replace("{question}", question);

        String response = chatClient.prompt()
            .user(prompt)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "route-" + sessionId))
            .call()
            .content();

        return parseIntentResult(response);
    }

    /**
     * 解析意图识别结果
     */
    private IntentResult parseIntentResult(String json) {
        try {
            // 提取JSON内容（处理可能的markdown代码块）
            String jsonContent = json;
            if (json.contains("```json")) {
                jsonContent = json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```"));
            } else if (json.contains("```")) {
                jsonContent = json.substring(json.indexOf("```") + 3, json.lastIndexOf("```"));
            }
            jsonContent = jsonContent.trim();

            // 简单解析JSON
            String targetAgent = extractValue(jsonContent, "targetAgent");
            String reason = extractValue(jsonContent, "reason");

            return new IntentResult(targetAgent, reason);
        } catch (Exception e) {
            log.error("解析意图结果失败: {}", json, e);
            return new IntentResult("GeneralAgent", "解析失败，默认使用通用Agent");
        }
    }

    /**
     * 从JSON中提取值
     */
    private String extractValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"";
        int start = json.indexOf(key);
        if (start == -1) return "";

        start = json.indexOf("\"", start + key.length() + 1);
        if (start == -1) return "";
        start++;

        int end = json.indexOf("\"", start);
        if (end == -1) return "";

        return json.substring(start, end);
    }

    /**
     * 查找Agent
     */
    private Agent findAgent(String agentName) {
        return agents.stream()
            .filter(agent -> agent.getName().equals(agentName))
            .findFirst()
            .orElse(null);
    }

    /**
     * 意图识别结果
     */
    private record IntentResult(String targetAgent, String reason) {}
}
