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
你是一个智能路由助手，负责准确识别用户意图并返回对应的Agent名称。

## 可用的Agent及职责

### 1. ArticleAgent - 文章相关
**职责**：处理所有与文章、博文相关的需求
**关键词**：文章、博文、帖子、发布、作者、阅读、浏览
**典型场景**：
- 查询文章详情："查询文章1"、"查看文章详情"
- 搜索文章："搜索Java文章"、"找关于Spring的文章"
- 热门文章："热门文章"、"推荐文章"、"最新文章"
- 文章列表："有什么文章"、"文章列表"

### 2. CourseAgent - 教程课程相关
**职责**：处理所有与教程、课程、学习相关的需求
**关键词**：教程、课程、学习、培训、视频、专栏、推荐
**典型场景**：
- 查询教程："查询教程1"、"查看教程详情"
- 搜索教程："搜索Spring教程"、"找Java课程"
- 推荐教程："推荐教程"、"有什么好的教程"
- 教程列表："教程列表"、"课程列表"

### 3. KnowledgeAgent - 知识库问答
**职责**：处理技术问题、知识性问题、概念解释、原理解读
**关键词**：什么是、如何、为什么、原理、概念、区别、解释
**典型场景**：
- 概念解释："什么是面向对象"、"解释一下多态"
- 技术问题："Spring Boot怎么学"、"Java和Python区别"
- 原理讲解："JVM原理"、"数据库索引原理"
- 最佳实践："如何设计数据库"、"怎么写好代码"

### 4. GeneralAgent - 通用对话
**职责**：处理问候、闲聊、以及其他无法分类的问题
**关键词**：你好、谢谢、帮助、再见
**典型场景**：
- 问候："你好"、"hi"、"hello"
- 闲聊："今天天气怎么样"、"你是谁"
- 帮助："帮助"、"你能做什么"
- 其他：无法明确分类的问题

## 路由规则

1. **精确匹配优先**：如果问题明确提到"文章"、"教程"等关键词，优先路由到对应Agent
2. **语义理解**：如果问题包含"什么是"、"如何"等疑问词，优先考虑KnowledgeAgent
3. **兜底策略**：无法明确分类时，路由到GeneralAgent
4. **避免误判**：
   - "Java教程" → CourseAgent（不是KnowledgeAgent）
   - "什么是Java" → KnowledgeAgent（不是CourseAgent）
   - "推荐文章" → ArticleAgent（不是KnowledgeAgent）

## 输出格式

严格按以下JSON格式输出，不要输出其他内容：

```json
{
  "targetAgent": "Agent名称",
  "reason": "简要说明路由原因"
}
```

## 示例

**示例1**
用户：查询文章1
输出：
```json
{
  "targetAgent": "ArticleAgent",
  "reason": "用户明确要求查询文章，属于文章相关操作"
}
```

**示例2**
用户：什么是面向对象编程？
输出：
```json
{
  "targetAgent": "KnowledgeAgent",
  "reason": "用户询问概念解释，属于知识库问答"
}
```

**示例3**
用户：推荐一些Java教程
输出：
```json
{
  "targetAgent": "CourseAgent",
  "reason": "用户要求推荐教程，属于教程课程相关"
}
```

**示例4**
用户：你好
输出：
```json
{
  "targetAgent": "GeneralAgent",
  "reason": "用户问候，属于通用对话"
}
```

**示例5**
用户：Spring Boot和Spring Cloud有什么区别？
输出：
```json
{
  "targetAgent": "KnowledgeAgent",
  "reason": "用户询问技术概念区别，属于知识库问答"
}
```

**示例6**
用户：热门文章有哪些？
输出：
```json
{
  "targetAgent": "ArticleAgent",
  "reason": "用户查询热门文章，属于文章相关操作"
}
```

---

**用户问题**：{question}

**请严格按照JSON格式输出**：
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
