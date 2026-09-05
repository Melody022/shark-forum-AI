package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.skill.Skill;
import com.itswy.paicodingai.skill.SkillRegistry;
import com.itswy.paicodingai.skilltree.SkillNode;
import com.itswy.paicodingai.skilltree.SkillRouter;
import com.itswy.paicodingai.skilltree.SkillTreeManager;
import com.itswy.paicodingai.tools.ArticleTools;
import com.itswy.paicodingai.tools.CourseTools;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * PaicodingAgent - 单一Agent
 *
 * 处理所有用户请求，通过Skill Router选择合适的Skill
 */
@Slf4j
@Component
public class PaicodingAgent extends AbstractAgent {

    @Autowired
    private SkillRouter skillRouter;

    @Autowired
    private SkillTreeManager skillTreeManager;

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private ArticleTools articleTools;

    @Autowired
    private CourseTools courseTools;

    @Autowired
    private SkillLoadTool skillLoadTool;

    public PaicodingAgent(ChatClient chatClient, SystemPromptConfig promptConfig) {
        super(chatClient, promptConfig);
    }

    @Override
    public String getName() {
        return "PaicodingAgent";
    }

    @Override
    public String getDescription() {
        return "技术派AI助手，处理文章推荐、教程推荐、知识问答等";
    }

    @Override
    protected String getSystemPrompt() {
        return promptConfig.getSystemMessage("paicoding");
    }

    /**
     * 处理用户问题
     */
    @Override
    public Flux<ChatEventVO> chat(String question, AgentContext ctx) {
        // 1. 路由选择Skill
        SkillNode skillNode = skillRouter.route(question);

        // 2. 构建系统提示词（包含Skill索引）
        String finalPrompt = buildSystemPromptWithSkillIndex(skillNode);

        // 3. 调用LLM（带工具）
        return doChat(question, finalPrompt, ctx, skillNode);
    }

    /**
     * 多模态对话仍复用 ChatClient、ChatMemory、Tool Calling 和 RAG，只额外附加图片媒体。
     */
    @Override
    public Flux<ChatEventVO> chat(String question, AgentContext ctx, List<String> imageUrls) {
        SkillNode skillNode = skillRouter.route(question == null ? "" : question);
        String finalPrompt = appendRagContext(buildSystemPromptWithSkillIndex(skillNode), question, ctx);
        List<Advisor> advisors = extraAdvisors();
        List<Media> media = imageUrls == null ? List.of() : imageUrls.stream().map(this::toMedia).toList();

        return chatClient.prompt()
                .system(finalPrompt)
                .user(user -> {
                    user.text(question == null || question.isBlank() ? "请分析图片。" : question);
                    if (!media.isEmpty()) {
                        user.media(media.toArray(Media[]::new));
                    }
                })
                .advisors(a -> {
                    a.advisors(advisors);
                    a.param(ChatMemory.CONVERSATION_ID, ctx.getSessionId());
                })
                .tools(skillLoadTool, articleTools, courseTools)
                .toolContext(java.util.Map.of("requestId", ctx.getRequestId()))
                .stream()
                .chatResponse()
                .map(response -> ChatEventVO.data(response.getResult().getOutput().getText()))
                .concatWith(getToolResult(ctx.getRequestId()));
    }

    private Media toMedia(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("图片地址不能为空");
        }
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("图片 data URL 格式无效");
            }
            String header = value.substring(5, comma);
            String encoded = value.substring(comma + 1);
            if (!header.endsWith(";base64")) {
                throw new IllegalArgumentException("图片 data URL 必须使用 base64");
            }
            String mimeName = header.substring(0, header.length() - ";base64".length());
            MimeType mimeType = MimeTypeUtils.parseMimeType(mimeName);
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return Media.builder().mimeType(mimeType).data(new ByteArrayResource(bytes)).build();
        }
        try {
            URI uri = URI.create(value);
            MimeType mimeType = value.toLowerCase().endsWith(".png")
                    ? MimeTypeUtils.IMAGE_PNG : MimeTypeUtils.IMAGE_JPEG;
            return new Media(mimeType, uri);
        } catch (Exception e) {
            throw new IllegalArgumentException("图片 URL 格式无效", e);
        }
    }

    /**
     * 构建带Skill索引的系统提示词
     */
    private String buildSystemPromptWithSkillIndex(SkillNode skillNode) {
        StringBuilder sb = new StringBuilder();

        // 基础提示词
        sb.append(getSystemPrompt()).append("\n\n");

        // Skill索引（很短）
        sb.append("## 可用的Skills\n\n");
        sb.append(buildSkillIndex());
        sb.append("\n\n");

        // 工具说明
        sb.append("## 可用的工具\n\n");
        sb.append("- load_skill: 加载指定Skill的完整内容，用于获取技能指南和操作规范\n");
        sb.append("- ArticleTools: 文章查询工具（查询文章、搜索文章、热门文章）\n");
        sb.append("- CourseTools: 教程查询工具（查询教程、搜索教程、推荐教程）\n");

        return sb.toString();
    }

    /**
     * 构建Skill索引（很短，只包含name和description）
     */
    private String buildSkillIndex() {
        return skillTreeManager.getAllNodes().stream()
            .filter(node -> node.getLevel() == 2)  // 只显示2层节点（细分场景）
            .map(node -> String.format("- **%s**: %s",
                node.getId(),
                node.getDescription()))
            .collect(Collectors.joining("\n"));
    }

    /**
     * 执行对话（带工具和RAG）
     */
    private Flux<ChatEventVO> doChat(String question, String systemPrompt, AgentContext ctx, SkillNode skillNode) {
        String finalPrompt = appendRagContext(systemPrompt, question, ctx);
        // 构建Advisors
        List<Advisor> advisors = extraAdvisors();

        return chatClient.prompt()
            .system(finalPrompt)
            .user(question)
            .advisors(a -> {
                a.advisors(advisors);
                a.param(ChatMemory.CONVERSATION_ID, ctx.getSessionId());
            })
            .tools(skillLoadTool, articleTools, courseTools)  // 注册所有工具
            .toolContext(java.util.Map.of("requestId", ctx.getRequestId()))
            .stream()
            .chatResponse()
            .map(response -> {
                var text = response.getResult().getOutput().getText();
                return ChatEventVO.data(text);
            })
            .concatWith(getToolResult(ctx.getRequestId()));
    }
}
