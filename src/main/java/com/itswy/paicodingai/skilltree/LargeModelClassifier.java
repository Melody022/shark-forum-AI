package com.itswy.paicodingai.skilltree;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 大模型兜底分类器
 *
 * 使用详细的prompt + few-shot处理复杂case
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LargeModelClassifier {

    private final ChatClient chatClient;
    private final SkillTreeManager skillTreeManager;

    /**
     * 使用大模型分析意图（兜底）
     */
    public ClassifyResult classify(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        try {
            // 详细的prompt + few-shot
            String prompt = buildDetailedPrompt(userInput);

            // 调用大模型
            String response = chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "intent-classify"))
                .call()
                .content();

            // 解析结果
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
     * 构建详细prompt（包含few-shot）
     */
    private String buildDetailedPrompt(String userInput) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个专业的意图分类专家。请根据用户输入，准确判断用户意图。\n\n");

        // 任务说明
        sb.append("## 任务说明\n\n");
        sb.append("请从以下意图中选择最匹配的一个，并返回JSON格式结果。\n\n");

        // 意图列表
        sb.append("## 可选意图\n\n");
        for (SkillNode node : skillTreeManager.getAllNodes()) {
            if (node.getLevel() == 2) {
                sb.append(String.format("- **%s**: %s\n", node.getId(), node.getDescription()));
            }
        }
        sb.append("\n");

        // Few-shot示例
        sb.append("## 分类示例\n\n");

        sb.append("示例1：\n");
        sb.append("用户输入：推荐热门文章\n");
        sb.append("分析：用户明确要求推荐文章，且是热门文章\n");
        sb.append("结果：{\"intent\": \"hot-article\", \"confidence\": 0.95, \"reason\": \"用户要求推荐热门文章\"}\n\n");

        sb.append("示例2：\n");
        sb.append("用户输入：搜索Java教程\n");
        sb.append("分析：用户要搜索教程，关键词是'教程'\n");
        sb.append("结果：{\"intent\": \"course-search\", \"confidence\": 0.92, \"reason\": \"用户搜索教程\"}\n\n");

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
            // 尝试解析JSON
            String cleaned = response.trim();

            // 提取intent
            String intent = extractJsonValue(cleaned, "intent");
            String confidenceStr = extractJsonValue(cleaned, "confidence");
            String reason = extractJsonValue(cleaned, "reason");

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
                // 字符串值
                start++;
                int end = json.indexOf("\"", start);
                if (end != -1) {
                    return json.substring(start, end);
                }
            } else {
                // 数字值
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
