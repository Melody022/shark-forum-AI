package com.itswy.paicodingai.skilltree;

import com.itswy.paicodingai.config.IntentModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 第 3 层 大模型兜底分类器(默认激活 provider = MIMO)。
 *
 * 携带详细 prompt + few-shot,覆盖复杂/边界 case。无阈值,只要解析出意图即接受。
 * 使用独立无记忆 ChatClient。
 */
@Slf4j
@Component
public class LargeModelClassifier {

    private final ChatClient chatClient;
    private final SkillTreeManager skillTreeManager;

    public LargeModelClassifier(IntentModelConfig intentModelConfig, SkillTreeManager skillTreeManager) {
        this.chatClient = intentModelConfig.largeIntentChatClient();
        this.skillTreeManager = skillTreeManager;
    }

    /**
     * 使用大模型分析意图(兜底)
     */
    public ClassifyResult classify(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }
        try {
            String prompt = buildDetailedPrompt(userInput);
            String response = chatClient.prompt().user(prompt).call().content();

            ClassifyResult result = parseResult(response);
            if (result != null) {
                log.info("大模型兜底分类: {} → {} (置信度: {})",
                    userInput, result.getIntent(), result.getConfidence());
            }
            return result;
        } catch (Exception e) {
            log.error("大模型兜底分类失败: {}", userInput, e);
            return null;
        }
    }

    /**
     * 构建详细 prompt(包含 few-shot)
     */
    private String buildDetailedPrompt(String userInput) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个专业的意图分类专家。请根据用户输入，准确判断用户意图。\n\n");

        sb.append("## 任务说明\n\n");
        sb.append("请从以下意图中选择最匹配的一个，并返回JSON格式结果。\n\n");

        // 意图列表（来自技能树 level2 + 固定动作意图）
        sb.append("## 可选意图\n\n");
        for (SkillNode node : skillTreeManager.getAllNodes()) {
            if (node.getLevel() == 2) {
                sb.append(String.format("- **%s**: %s\n", node.getId(), node.getDescription()));
            }
        }
        sb.append("- **human-service**: 转人工客服 / 人工服务(用户明确要人工介入)\n");
        sb.append("\n");

        // Few-shot 示例（意图 id 必须与上方一致）
        sb.append("## 分类示例\n\n");

        sb.append("示例1：\n");
        sb.append("用户输入：推荐热门文章\n");
        sb.append("分析：用户明确要求推荐文章，且是热门文章\n");
        sb.append("结果：{\"intent\": \"hot-article\", \"confidence\": 0.95, \"reason\": \"用户要求推荐热门文章\"}\n\n");

        sb.append("示例2：\n");
        sb.append("用户输入：搜索Java教程\n");
        sb.append("分析：用户要搜索教程，关键词是'教程'\n");
        sb.append("结果：{\"intent\": \"search-course\", \"confidence\": 0.92, \"reason\": \"用户搜索教程\"}\n\n");

        sb.append("示例3：\n");
        sb.append("用户输入：什么是面向对象\n");
        sb.append("分析：用户询问概念解释，'什么是'是典型的概念解释意图\n");
        sb.append("结果：{\"intent\": \"concept-explanation\", \"confidence\": 0.93, \"reason\": \"用户询问概念解释\"}\n\n");

        sb.append("示例4：\n");
        sb.append("用户输入：有没有好的学习资源\n");
        sb.append("分析：用户寻找学习资源，包括教程和课程\n");
        sb.append("结果：{\"intent\": \"recommend-course\", \"confidence\": 0.88, \"reason\": \"用户寻找学习资源\"}\n\n");

        sb.append("示例5：\n");
        sb.append("用户输入：Spring Boot和Spring Cloud有什么区别\n");
        sb.append("分析：用户询问两个框架的区别，是概念解释\n");
        sb.append("结果：{\"intent\": \"concept-explanation\", \"confidence\": 0.91, \"reason\": \"用户询问框架区别\"}\n\n");

        sb.append("示例6：\n");
        sb.append("用户输入：帮我转人工客服\n");
        sb.append("分析：用户明确要求人工介入\n");
        sb.append("结果：{\"intent\": \"human-service\", \"confidence\": 0.97, \"reason\": \"用户要求转人工\"}\n\n");

        // 用户输入
        sb.append("## 当前任务\n\n");
        sb.append("用户输入：").append(userInput).append("\n\n");
        sb.append("请分析用户意图，返回JSON格式结果：\n");
        sb.append("{\"intent\": \"意图ID\", \"confidence\": 0.9, \"reason\": \"简要原因\"}\n");

        return sb.toString();
    }

    /**
     * 解析大模型返回结果
     */
    private ClassifyResult parseResult(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            String intent = extractJsonValue(response, "intent");
            String confidenceStr = extractJsonValue(response, "confidence");
            String reason = extractJsonValue(response, "reason");
            if (intent != null && confidenceStr != null) {
                double confidence = Double.parseDouble(confidenceStr);
                return new ClassifyResult(intent, confidence, "large-model", reason);
            }
            return null;
        } catch (Exception e) {
            log.error("解析大模型结果失败: {}", response, e);
            return null;
        }
    }

    /**
     * 从JSON字符串中提取值
     */
    private String extractJsonValue(String json, String key) {
        try {
            int start = json.indexOf("\"" + key + "\"");
            if (start == -1) return null;

            start = json.indexOf(":", start) + 1;
            while (start < json.length() && json.charAt(start) == ' ') start++;

            if (start < json.length() && json.charAt(start) == '"') {
                start++;
                int end = json.indexOf("\"", start);
                if (end != -1) {
                    return json.substring(start, end);
                }
            } else {
                int end = start;
                while (end < json.length() &&
                       (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
                    end++;
                }
                if (end > start) {
                    return json.substring(start, end);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
