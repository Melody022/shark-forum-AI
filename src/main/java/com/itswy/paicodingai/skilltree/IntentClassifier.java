package com.itswy.paicodingai.skilltree;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图分类器 - 三层漏斗架构
 *
 * 1. 规则匹配（关键词+正则）
 * 2. DeepSeek小模型
 * 3. 大模型兜底（详细prompt+few-shot）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentClassifier {

    private final RuleBasedClassifier ruleClassifier;
    private final DeepSeekSmallModelClassifier smallModelClassifier;
    private final LargeModelClassifier largeModelClassifier;
    private final SkillTreeManager skillTreeManager;

    /** 小模型置信度阈值 */
    private static final double SMALL_MODEL_THRESHOLD = 0.7;

    /**
     * 意图分类
     */
    public ClassifyResult classify(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return createDefaultResult();
        }

        log.info("开始意图分类: {}", userInput);

        // 第1层：规则匹配
        ClassifyResult ruleResult = ruleClassifier.classify(userInput);
        if (ruleResult != null) {
            log.info("第1层规则匹配成功: {} → {}", userInput, ruleResult.getIntent());
            return ruleResult;
        }

        // 第2层：DeepSeek小模型
        ClassifyResult smallModelResult = smallModelClassifier.classify(userInput);
        if (smallModelResult != null && smallModelResult.getConfidence() >= SMALL_MODEL_THRESHOLD) {
            log.info("第2层小模型成功: {} → {} (置信度: {})",
                userInput, smallModelResult.getIntent(), smallModelResult.getConfidence());
            return smallModelResult;
        }

        // 第3层：大模型兜底（使用详细prompt+few-shot）
        log.info("第3层大模型兜底: {}", userInput);
        ClassifyResult largeModelResult = largeModelClassifier.classify(userInput);
        if (largeModelResult != null) {
            log.info("第3层大模型成功: {} → {} (置信度: {})",
                userInput, largeModelResult.getIntent(), largeModelResult.getConfidence());
            return largeModelResult;
        }

        // 所有方法都失败，返回默认
        log.warn("意图分类失败，使用默认: {}", userInput);
        return createDefaultResult();
    }

    /**
     * 获取分类统计
     */
    public ClassifyStats getStats() {
        return new ClassifyStats(
            ruleClassifier != null ? "active" : "inactive",
            smallModelClassifier != null ? "active" : "inactive",
            largeModelClassifier != null ? "active" : "inactive"
        );
    }

    /**
     * 创建默认结果（通用对话）
     */
    private ClassifyResult createDefaultResult() {
        return new ClassifyResult("general-chat", 0.5, "default", "未匹配到特定意图");
    }

    /**
     * 分类统计
     */
    @Data
    public static class ClassifyStats {
        private final String ruleStatus;
        private final String smallModelStatus;
        private final String largeModelStatus;
    }
}
