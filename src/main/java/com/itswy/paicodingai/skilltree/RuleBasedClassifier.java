package com.itswy.paicodingai.skilltree;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 基于规则的意图分类器
 */
@Slf4j
@Component
public class RuleBasedClassifier {

    /** 关键词规则 */
    private static final Map<String, List<String>> KEYWORD_RULES = Map.ofEntries(
        Map.entry("hot-article", List.of("热门文章", "推荐文章", "最新文章", "排行", "热门")),
        Map.entry("article-search", List.of("搜索文章", "查找文章", "找文章", "关于", "文章搜索")),
        Map.entry("article-detail", List.of("文章详情", "查看文章", "具体内容", "文章内容")),
        Map.entry("recommend-course", List.of("推荐教程", "推荐课程", "好的教程", "学习资源", "入门教程")),
        Map.entry("course-search", List.of("搜索教程", "查找教程", "找教程", "教程搜索")),
        Map.entry("learning-path", List.of("学习路线", "怎么学", "入门到精通", "学习规划")),
        Map.entry("concept-explanation", List.of("什么是", "解释一下", "概念", "定义", "原理")),
        Map.entry("technical-qa", List.of("如何", "怎么", "为什么", "问题", "怎么办")),
        Map.entry("best-practice", List.of("最佳实践", "建议", "优化", "性能", "技巧")),
        Map.entry("greeting", List.of("你好", "hi", "hello", "嗨", "早上好", "下午好")),
        Map.entry("help", List.of("帮助", "功能", "能做什么", "使用方法", "说明"))
    );

    /** 正则规则 */
    private static final Map<String, Pattern> REGEX_RULES = Map.of(
        "article-detail", Pattern.compile(".*查看文章\\d+.*|.*文章\\d+的详情.*|.*文章\\d+.*"),
        "course-search", Pattern.compile(".*搜索.+教程.*|.*查找.+课程.*|.*找.+教程.*")
    );

    /**
     * 规则匹配分类
     */
    public ClassifyResult classify(String userInput) {
        List<String> candidates = getCandidates(userInput);

        if (candidates.isEmpty()) {
            return null;
        }

        // 返回第一个匹配的候选
        return new ClassifyResult(candidates.get(0), 1.0, "keyword", null);
    }

    /**
     * 获取所有候选意图（用于后续验证）
     */
    public List<String> getCandidates(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        String lowerInput = userInput.toLowerCase();

        // 1. 关键词匹配
        for (Map.Entry<String, List<String>> entry : KEYWORD_RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerInput.contains(keyword.toLowerCase())) {
                    if (!candidates.contains(entry.getKey())) {
                        candidates.add(entry.getKey());
                        log.debug("关键词匹配: {} → {} (关键词: {})",
                            userInput, entry.getKey(), keyword);
                    }
                }
            }
        }

        // 2. 正则匹配
        for (Map.Entry<String, Pattern> entry : REGEX_RULES.entrySet()) {
            if (entry.getValue().matcher(lowerInput).matches()) {
                if (!candidates.contains(entry.getKey())) {
                    candidates.add(entry.getKey());
                    log.debug("正则匹配: {} → {}", userInput, entry.getKey());
                }
            }
        }

        return candidates;
    }
}
