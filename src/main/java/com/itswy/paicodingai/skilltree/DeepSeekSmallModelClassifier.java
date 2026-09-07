package com.itswy.paicodingai.skilltree;

import com.itswy.paicodingai.config.IntentModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 第 2 层 小模型分类器(默认本地 Ollama)。
 *
 * <p>提示词简单、不携带 few-shot,置信度低于 {@link #CONFIDENCE_THRESHOLD} 时返回 null,
 * 交由第 3 层大模型兜底。使用独立无记忆 ChatClient,避免会话记忆污染分类。</p>
 */
@Slf4j
@Component
public class DeepSeekSmallModelClassifier {

    /** 小模型置信度阈值 */
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    private final ChatClient chatClient;

    public DeepSeekSmallModelClassifier(IntentModelConfig intentModelConfig) {
        this.chatClient = intentModelConfig.smallIntentChatClient();
    }

    /**
     * 使用小模型分析意图
     */
    public ClassifyResult classify(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        try {
            String prompt = buildSimplePrompt(userInput);
            String response = chatClient.prompt().user(prompt).call().content();

            ClassifyResult result = parseResult(response);
            if (result != null && result.getConfidence() >= CONFIDENCE_THRESHOLD) {
                log.info("小模型分类: {} → {} (置信度: {})",
                    userInput, result.getIntent(), result.getConfidence());
                return result;
            }

            log.info("小模型置信度不足: {} (置信度: {})",
                userInput, result != null ? result.getConfidence() : 0);
            return null;

        } catch (Exception e) {
            log.error("小模型分类失败: {}", userInput, e);
            return null;
        }
    }

    /**
     * 构建简单 prompt(不携带 few-shot)
     */
    private String buildSimplePrompt(String userInput) {
        return String.format("""
            请分析用户输入的意图，从以下选项中选择最匹配的一个。

            用户输入：%s

            可选意图：
            - hot-article: 推荐热门文章
            - article-search: 搜索文章
            - article-detail: 查看文章详情
            - recommend-course: 推荐教程
            - search-course: 搜索教程
            - learning-path: 学习路线
            - concept-explanation: 解释概念
            - technical-qa: 技术问题
            - best-practice: 最佳实践
            - greeting: 问候
            - help: 帮助
            - human-service: 转人工客服 / 人工服务

            请返回JSON格式：
            {"intent": "意图ID", "confidence": 0.9, "reason": "简要原因"}
            """, userInput);
    }

    /**
     * 解析小模型返回结果
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
                return new ClassifyResult(intent, confidence, "small-model", reason);
            }
            return null;
        } catch (Exception e) {
            log.error("解析小模型结果失败: {}", response, e);
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
