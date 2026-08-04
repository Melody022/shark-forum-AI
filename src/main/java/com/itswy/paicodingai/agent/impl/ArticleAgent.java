package com.itswy.paicodingai.agent.impl;

import com.itswy.paicodingai.agent.AbstractAgent;
import com.itswy.paicodingai.config.SystemPromptConfig;
import com.itswy.paicodingai.tools.ArticleTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 文章Agent
 *
 * 处理文章相关查询，使用通用RAG功能
 */
@Component
public class ArticleAgent extends AbstractAgent {

    private final ArticleTools articleTools;

    public ArticleAgent(ChatClient chatClient,
                       SystemPromptConfig promptConfig,
                       ArticleTools articleTools) {
        super(chatClient, promptConfig);
        this.articleTools = articleTools;
        // 启用RAG
        this.enableRAG = true;
        this.ragTopK = 3;
    }

    @Override
    public String getName() {
        return "ArticleAgent";
    }

    @Override
    public String getDescription() {
        return "处理文章相关查询";
    }

    @Override
    protected String getSystemPrompt() {
        return "你是文章助手，专门处理文章相关查询。\n" +
               "你可以使用以下工具：\n" +
               "- queryArticleById：根据ID查询文章详情\n" +
               "- queryArticleList：查询文章列表\n" +
               "- searchArticles：搜索文章\n" +
               "- queryHotArticles：查询热门文章\n" +
               "请根据用户问题选择合适的工具。\n" +
               "如果知识库中有相关参考资料，请结合参考资料回答。";
    }
}
