package com.itswy.paicodingai.agent;

import com.itswy.paicodingai.entity.AgentDefinition;
import com.itswy.paicodingai.mcp.McpServerManager;
import com.itswy.paicodingai.service.HumanQueueService;
import com.itswy.paicodingai.service.prompt.PromptAssembler;
import com.itswy.paicodingai.skill.Skill;
import com.itswy.paicodingai.skilltree.ClassifyResult;
import com.itswy.paicodingai.skilltree.SkillNode;
import com.itswy.paicodingai.skilltree.SkillRouter;
import com.itswy.paicodingai.skilltree.SkillTreeManager;
import com.itswy.paicodingai.tools.ToolRegistry;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 主 Agent(DB 驱动,单 Agent + 多 skill/tool)。
 *
 * <p>系统提示词来自 {@link PromptAssembler}(ai_prompt,base+agent);
 * 工具集由 {@link AgentFactory} 按「角色白名单 ∩ ai_agent 声明」计算并经 {@link ToolRegistry} 还原实例,
 * 不再硬编码 .tools(skillLoadTool, articleTools, courseTools)。</p>
 */
@Slf4j
@Component
public class PaicodingAgent extends AbstractAgent {

    @Autowired
    private SkillRouter skillRouter;

    @Autowired
    private SkillTreeManager skillTreeManager;

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private PromptAssembler promptAssembler;

    @Autowired
    private McpServerManager mcpServerManager;

    @Autowired
    private HumanQueueService humanQueueService;

    /** 转人工意图 id(与分类器/规则一致)。 */
    public static final String INTENT_HUMAN_SERVICE = "human-service";

    public PaicodingAgent(ChatClient chatClient, PromptAssembler promptAssembler) {
        super(chatClient);
        this.promptAssembler = promptAssembler;
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
        AgentDefinition def = agentFactory.getAgent(AgentFactory.DEFAULT_AGENT_CODE);
        String promptKey = def != null ? def.getPromptKey() : null;
        return promptAssembler.assemble(promptKey);
    }

    /**
     * 处理用户问题
     */
    @Override
    public Flux<ChatEventVO> chat(String question, AgentContext ctx) {
        ClassifyResult classifyResult = skillRouter.classify(question);
        // 转人工:不入模型,直接入队 + 话术
        if (INTENT_HUMAN_SERVICE.equals(classifyResult.getIntent())) {
            return humanServiceFlow(classifyResult, ctx);
        }
        SkillNode skillNode = skillRouter.resolve(classifyResult);
        String finalPrompt = buildSystemPromptWithSkillIndex(skillNode);
        List<Object> toolBeans = resolveTools(ctx);
        Flux<ChatEventVO> answer = doChat(question, finalPrompt, ctx, skillNode, toolBeans);
        return Flux.concat(
                Flux.just(ChatEventVO.intent(
                        classifyResult.getIntent(), classifyResult.getConfidence(), classifyResult.getMethod())),
                answer);
    }

    /**
     * 多模态对话仍复用 ChatClient、ChatMemory、Tool Calling 和 RAG，只额外附加图片媒体。
     */
    @Override
    public Flux<ChatEventVO> chat(String question, AgentContext ctx, List<String> imageUrls) {
        ClassifyResult classifyResult = skillRouter.classify(question == null ? "" : question);
        if (INTENT_HUMAN_SERVICE.equals(classifyResult.getIntent())) {
            return humanServiceFlow(classifyResult, ctx);
        }
        SkillNode skillNode = skillRouter.resolve(classifyResult);
        String finalPrompt = appendRagContext(buildSystemPromptWithSkillIndex(skillNode), question, ctx);
        List<Advisor> advisors = extraAdvisors();
        List<Media> media = imageUrls == null ? List.of() : imageUrls.stream().map(this::toMedia).toList();
        List<Object> toolBeans = resolveTools(ctx);

        Flux<ChatEventVO> answer = chatClient.prompt()
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
                .tools(toolBeans.toArray())
                .toolContext(java.util.Map.of(
                        "requestId", ctx.getRequestId(),
                        "userId", ctx.getUserId() == null ? "" : ctx.getUserId(),
                        "role", ctx.getRoleCode() == null ? AgentFactory.DEFAULT_ROLE : ctx.getRoleCode()))
                .stream()
                .chatResponse()
                .flatMap(AbstractAgent::mapTextEvent)
                .concatWith(getToolResult(ctx.getRequestId()));

        return Flux.concat(
                Flux.just(ChatEventVO.intent(
                        classifyResult.getIntent(), classifyResult.getConfidence(), classifyResult.getMethod())),
                answer);
    }

    /**
     * 按角色解析可用工具实例(白名单 ∩ Agent 声明)。
     */
    private List<Object> resolveTools(AgentContext ctx) {
        String role = ctx == null || ctx.getRoleCode() == null || ctx.getRoleCode().isBlank()
                ? AgentFactory.DEFAULT_ROLE : ctx.getRoleCode();
        List<String> names = agentFactory.allowedToolNames(AgentFactory.DEFAULT_AGENT_CODE, role);
        if (names.isEmpty()) {
            log.warn("该角色没有任何可用工具: role={}", role);
        }
        List<Object> tools = new java.util.ArrayList<>(toolRegistry.resolve(names));
        // 追加该 Agent 声明 MCP server 提供的本地工具
        tools.addAll(mcpServerManager.resolveDeclaredLocalTools(AgentFactory.DEFAULT_AGENT_CODE));
        return tools;
    }

    /**
     * 转人工短路:入队 + 话术,不调用大模型。
     */
    private Flux<ChatEventVO> humanServiceFlow(ClassifyResult classifyResult, AgentContext ctx) {
        humanQueueService.enqueue(ctx.getSessionId(), ctx.getUserId());
        String message = "已为您转接人工客服，请稍候。若仍有问题可继续留言，人工坐席接入后即可看到当前会话。";
        return Flux.concat(
                Flux.just(ChatEventVO.intent(
                        classifyResult.getIntent(), classifyResult.getConfidence(), classifyResult.getMethod())),
                Flux.just(ChatEventVO.data(message)));
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

        // 基础提示词(base + 主Agent,均来自 DB)
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
    private Flux<ChatEventVO> doChat(String question, String systemPrompt, AgentContext ctx,
                                     SkillNode skillNode, List<Object> toolBeans) {
        String finalPrompt = appendRagContext(systemPrompt, question, ctx);
        List<Advisor> advisors = extraAdvisors();

        return chatClient.prompt()
            .system(finalPrompt)
            .user(question)
            .advisors(a -> {
                a.advisors(advisors);
                a.param(ChatMemory.CONVERSATION_ID, ctx.getSessionId());
            })
            .tools(toolBeans.toArray())  // 动态白名单工具
            .toolContext(java.util.Map.of(
                    "requestId", ctx.getRequestId(),
                    "userId", ctx.getUserId() == null ? "" : ctx.getUserId(),
                    "role", ctx.getRoleCode() == null ? AgentFactory.DEFAULT_ROLE : ctx.getRoleCode()))
            .stream()
            .chatResponse()
            .flatMap(AbstractAgent::mapTextEvent)
            .concatWith(getToolResult(ctx.getRequestId()));
    }
}
