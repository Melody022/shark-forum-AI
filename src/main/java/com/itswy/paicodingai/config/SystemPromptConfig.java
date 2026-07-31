package com.itswy.paicodingai.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ==========================================================================
 * 系统提示词配置 —— 从 prompts/ 目录的 Markdown 文件读取
 * ==========================================================================
 *
 * 提示词分层架构：
 *
 *   1. base.md —— 全局核心规则（所有智能体共享，不变）
 *   2. agents/xxx.md —— 每个智能体的专属规则
 *   3. skills/xxx.md —— 按需加载的 Skill（后续实现）
 *
 * 组装顺序（固定在前、动态在后，优化 LLM KV 缓存命中）：
 *   base.md + agents/xxx.md + skills/xxx.md + 历史消息 + 用户问题
 *
 * @date 2026-07-31
 */
@Slf4j
@Component
public class SystemPromptConfig {

    /** 全局核心规则（不变） */
    private String basePrompt;

    /** 各智能体的专属规则（按 AgentType 切换） */
    private final Map<String, String> agentPrompts = new HashMap<>();

    /**
     * 启动时加载所有提示词文件
     * 文件在 classpath 的 prompts/ 目录下
     */
    @PostConstruct
    public void init() {
        // 加载全局规则
        basePrompt = loadFile("prompts/base.md");

        // 加载各智能体的专属规则（文件名和 AgentTypeEnum 的 name 一致）
        agentPrompts.put("ROUTE", loadFile("prompts/agents/route.md"));
        agentPrompts.put("ARTICLE", loadFile("prompts/agents/article.md"));
        agentPrompts.put("KNOWLEDGE", loadFile("prompts/agents/knowledge.md"));
        agentPrompts.put("GENERAL", loadFile("prompts/agents/general.md"));

        log.info("提示词加载完成：base + {} 个 Agent 专属规则", agentPrompts.size());
    }

    /**
     * 获取完整的系统提示词
     *
     * 组装逻辑：base（不变） + agent 专属规则（按类型切换）
     * 后续加 Skill 时，在中间插入 skills/xxx.md
     *
     * @param agentType 智能体类型（如 GENERAL、ARTICLE 等）
     * @return 拼装后的完整 system prompt
     */
    public String getSystemMessage(String agentType) {
        String agentPrompt = agentPrompts.getOrDefault(agentType, "");
        return basePrompt + "\n" + agentPrompt;
    }

    /**
     * 获取全局核心规则（某些场景只需要 base，不需要 agent 规则）
     */
    public String getBasePrompt() {
        return basePrompt;
    }

    /**
     * 从 classpath 读取文件内容
     *
     * @param path classpath 下的相对路径（如 "prompts/base.md"）
     * @return 文件内容，读取失败返回空字符串
     */
    private String loadFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String content = StreamUtils.copyToString(
                    resource.getInputStream(), StandardCharsets.UTF_8);
            log.debug("加载提示词文件：{}", path);
            return content;
        } catch (IOException e) {
            log.warn("提示词文件不存在或读取失败：{}", path);
            return "";
        }
    }
}
