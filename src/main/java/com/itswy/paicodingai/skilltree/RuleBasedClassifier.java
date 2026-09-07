package com.itswy.paicodingai.skilltree;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于规则的意图分类器(第 1 层,零成本快通)。
 *
 * <p>采用「最长命中加分 + 插入顺序决胜」的确定性打分,避免无序 Map 导致的多意图命中随机性;
 * 命中即返回,不调用任何模型。</p>
 */
@Slf4j
@Component
public class RuleBasedClassifier {

    /** 关键词规则(LinkedHashMap 保证插入顺序作为同分决胜的优先级) */
    private static final Map<String, List<String>> KEYWORD_RULES = new LinkedHashMap<>();

    static {
        KEYWORD_RULES.put("human-service", List.of("转人工", "人工客服", "转接人工", "人工服务", "人工接入", "找真人", "找人工"));
        KEYWORD_RULES.put("hot-article", List.of("推荐热门文章", "热门文章", "推荐文章", "最新文章", "文章排行", "热门"));
        KEYWORD_RULES.put("article-search", List.of("搜索文章", "查找文章", "找文章", "文章搜索"));
        KEYWORD_RULES.put("article-detail", List.of("文章详情", "查看文章", "具体内容", "文章内容"));
        KEYWORD_RULES.put("learning-path", List.of("学习路线", "怎么学", "入门到精通", "学习规划"));
        KEYWORD_RULES.put("recommend-course", List.of("推荐教程", "推荐课程", "好的教程", "学习资源", "入门教程"));
        KEYWORD_RULES.put("search-course", List.of("搜索教程", "查找教程", "找教程", "教程搜索"));
        KEYWORD_RULES.put("concept-explanation", List.of("什么是", "解释一下", "概念", "定义", "原理"));
        KEYWORD_RULES.put("technical-qa", List.of("如何", "怎么", "为什么", "怎么办"));
        KEYWORD_RULES.put("best-practice", List.of("最佳实践", "建议", "优化", "性能", "技巧"));
        KEYWORD_RULES.put("greeting", List.of("你好", "hi", "hello", "嗨", "早上好", "下午好"));
        KEYWORD_RULES.put("help", List.of("帮助", "能做什么", "使用方法", "功能说明"));
    }

    /** 正则规则(整串匹配,命中视为强信号) */
    private static final Map<String, Pattern> REGEX_RULES = new LinkedHashMap<>();

    static {
        REGEX_RULES.put("article-detail", Pattern.compile(".*查看文章\\d+.*|.*文章\\d+的详情.*"));
        REGEX_RULES.put("search-course", Pattern.compile(".*搜索.+教程.*|.*查找.+课程.*|.*找.+教程.*"));
        REGEX_RULES.put("article-search", Pattern.compile(".*搜.+文章.*|.*找.+文章.*"));
        REGEX_RULES.put("human-service", Pattern.compile(".*(转人工|人工客服|人工服务).*"));
    }

    /**
     * 规则匹配分类;未命中返回 null。
     */
    public ClassifyResult classify(String userInput) {
        String best = bestCandidate(userInput);
        if (best == null) {
            return null;
        }
        return new ClassifyResult(best, 1.0, "keyword", null);
    }

    /**
     * 获取所有候选意图(按关键词/正则命中,顺序即打分后的降序)
     */
    public List<String> getCandidates(String userInput) {
        return rankedCandidates(userInput);
    }

    private String bestCandidate(String userInput) {
        List<String> ranked = rankedCandidates(userInput);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /**
     * 计算候选并按「命中长度降序、规则声明顺序决胜」排序。
     */
    private List<String> rankedCandidates(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return List.of();
        }
        String lower = userInput.toLowerCase();
        Map<String, Integer> score = new LinkedHashMap<>();

        // 关键词：累加命中关键词长度，越具体(长关键词)分越高
        for (Map.Entry<String, List<String>> entry : KEYWORD_RULES.entrySet()) {
            int hit = 0;
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword.toLowerCase())) {
                    hit += keyword.length();
                }
            }
            if (hit > 0) {
                score.put(entry.getKey(), hit);
            }
        }
        // 正则：整串匹配作为强信号(权重叠加,且不冲突关键词)
        for (Map.Entry<String, Pattern> entry : REGEX_RULES.entrySet()) {
            if (entry.getValue().matcher(lower).matches()) {
                score.merge(entry.getKey(), 1000, Integer::sum);
            }
        }

        if (score.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(score.entrySet()).stream()
                .sorted((a, b) -> {
                    int byScore = b.getValue().compareTo(a.getValue());
                    return byScore != 0 ? byScore : 0; // 同分保持声明顺序(LinkedHashMap 稳定排序)
                })
                .map(Map.Entry::getKey)
                .toList();
    }
}
