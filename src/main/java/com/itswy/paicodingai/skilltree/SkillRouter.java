package com.itswy.paicodingai.skilltree;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Skill Router - 使用三级漏斗架构
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRouter {

    private final SkillTreeManager skillTreeManager;
    private final IntentClassifier intentClassifier;

    /**
     * 路由选择Skill
     */
    public SkillNode route(String userInput) {
        log.info("Skill Router开始处理: {}", userInput);

        // 使用意图分类器（三级漏斗）
        ClassifyResult classifyResult = intentClassifier.classify(userInput);
        log.info("意图分类结果: {}", classifyResult);

        // 根据意图查找SkillNode
        SkillNode selected = skillTreeManager.getNode(classifyResult.getIntent());

        if (selected == null) {
            // 如果找不到对应的节点，尝试模糊匹配
            selected = findSimilarNode(classifyResult.getIntent());
        }

        if (selected == null) {
            // 默认返回通用对话
            selected = skillTreeManager.getNode("general-category");
            log.warn("未找到匹配的Skill，使用默认: general-category");
        }

        log.info("路由完成: {} → {} (置信度: {}, 方法: {})",
            userInput, selected.getId(), classifyResult.getConfidence(), classifyResult.getMethod());

        return selected;
    }

    /**
     * 模糊匹配节点
     */
    private SkillNode findSimilarNode(String intent) {
        // 尝试匹配1层节点
        for (SkillNode node : skillTreeManager.getRootNodes()) {
            if (node.getId().contains(intent) || intent.contains(node.getId())) {
                return node;
            }
        }

        // 尝试匹配2层节点
        for (SkillNode node : skillTreeManager.getAllNodes()) {
            if (node.getLevel() == 2) {
                if (node.getId().contains(intent) || intent.contains(node.getId())) {
                    return node;
                }
            }
        }

        return null;
    }

    /**
     * 获取路由统计
     */
    public RouteStats getStats() {
        IntentClassifier.ClassifyStats classifyStats = intentClassifier.getStats();
        return new RouteStats(
            skillTreeManager.getAllNodes().size(),
            classifyStats
        );
    }

    @Data
    public static class RouteStats {
        private final int totalNodes;
        private final IntentClassifier.ClassifyStats classifyStats;
    }
}
