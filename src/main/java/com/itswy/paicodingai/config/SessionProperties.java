package com.itswy.paicodingai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * ==========================================================================
 * 会话配置 —— 从 application.yml 读取
 * ==========================================================================
 *
 * 这个类对应 yml 中 ai.session 的配置：
 *
 *   ai:
 *     session:
 *       title: "HELLO, 我是AI助手"
 *       describe: "我是由技术派打造的智能助理..."
 *       examples:
 *         - "能帮我推荐一个教程吗？"
 *         - "Java和Python有什么区别？"
 *
 * @ConfigurationProperties(prefix = "ai.session") 的意思是：
 *   把 yml 中所有以 ai.session 开头的配置，
 *   自动绑定到这个类的字段上。
 *
 * @date 2026-07-18
 */
@Component
@ConfigurationProperties(prefix = "ai.session")
public class SessionProperties {

    /**
     * AI助手的标题
     * 显示在聊天窗口顶部
     */
    private String title;

    /**
     * AI助手的描述
     * 显示在标题下方，简单介绍功能
     */
    private String describe;

    /**
     * 示例问题列表
     * 新会话时随机选几个展示给用户
     */
    private List<String> examples;

    // ===== getter/setter =====

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }
}
