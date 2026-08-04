package com.itswy.paicodingai.agent.impl;

import com.itswy.paicodingai.agent.AbstractAgent;
import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.tools.CourseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 教程Agent
 *
 * 处理教程课程相关查询，使用通用RAG功能
 */
@Component
public class CourseAgent extends AbstractAgent {

    private final CourseTools courseTools;

    public CourseAgent(ChatClient chatClient,
                      SystemPromptConfig promptConfig,
                      CourseTools courseTools) {
        super(chatClient, promptConfig);
        this.courseTools = courseTools;
        // 启用RAG
        this.enableRAG = true;
        this.ragTopK = 3;
    }

    @Override
    public String getName() {
        return "CourseAgent";
    }

    @Override
    public String getDescription() {
        return "处理教程课程相关查询";
    }

    @Override
    protected String getSystemPrompt() {
        return "你是教程助手，专门处理教程课程相关查询。\n" +
               "你可以使用以下工具：\n" +
               "- queryCourseById：根据ID查询教程详情\n" +
               "- queryCourseList：查询教程列表\n" +
               "- searchCourses：搜索教程\n" +
               "- queryRecommendedCourses：查询推荐教程\n" +
               "请根据用户问题选择合适的工具。\n" +
               "如果知识库中有相关参考资料，请结合参考资料回答。";
    }
}
