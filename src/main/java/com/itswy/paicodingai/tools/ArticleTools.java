package com.itswy.paicodingai.tools;

import com.itswy.paicodingai.service.forum.ForumArticleService;
import com.itswy.paicodingai.tools.result.ArticleInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文章工具 - Tool Calling实现
 *
 * 直接查询 forum 数据库，不依赖微服务调用
 * 参考天机学堂实现：Tool执行结果存入ToolResultHolder，SSE流最后返回给前端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleTools {

    private final ForumArticleService forumArticleService;

    /**
     * 根据ID查询文章详情
     *
     * @param articleId 文章ID
     * @param toolContext 工具上下文（包含requestId）
     * @return 文章详情
     */
    @Tool(description = "根据文章ID查询文章详情，返回文章标题、内容、作者等信息")
    public ArticleInfo queryArticleById(
            @ToolParam(description = "文章ID") Long articleId,
            ToolContext toolContext) {
        log.info("查询文章详情: articleId={}", articleId);
        try {
            ArticleInfo articleInfo = forumArticleService.getArticleById(articleId);
            if (articleInfo == null) {
                throw new RuntimeException("文章不存在: articleId=" + articleId);
            }

            // ★ 关键：将查询结果存入ToolResultHolder，供SSE流最后返回给前端
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null) {
                String fieldKey = "articleInfo_" + articleId;
                ToolResultHolder.put(requestId, fieldKey, articleInfo);
                log.info("文章查询结果已存入ToolResultHolder: requestId={}, field={}", requestId, fieldKey);
            }

            return articleInfo;
        } catch (Exception e) {
            log.error("查询文章失败: articleId={}", articleId, e);
            throw new RuntimeException("文章查询失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询文章列表
     *
     * @param page 页码
     * @param size 每页数量
     * @param toolContext 工具上下文
     * @return 文章列表
     */
    @Tool(description = "查询文章列表，支持分页，返回最新发布的文章")
    public List<ArticleInfo> queryArticleList(
            @ToolParam(description = "页码，默认1") Integer page,
            @ToolParam(description = "每页数量，默认10") Integer size,
            ToolContext toolContext) {
        log.info("查询文章列表: page={}, size={}", page, size);
        try {
            if (page == null || page <= 0) {
                page = 1;
            }
            if (size == null || size <= 0) {
                size = 10;
            }
            List<ArticleInfo> articles = forumArticleService.getArticleList(page, size);

            // 存入ToolResultHolder（取第一个文章作为代表）
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null && !articles.isEmpty()) {
                ToolResultHolder.put(requestId, "articleList", articles);
                log.info("文章列表已存入ToolResultHolder: requestId={}, count={}", requestId, articles.size());
            }

            return articles;
        } catch (Exception e) {
            log.error("查询文章列表失败", e);
            throw new RuntimeException("文章列表查询失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据关键词搜索文章
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量限制
     * @param toolContext 工具上下文
     * @return 文章列表
     */
    @Tool(description = "根据关键词搜索文章，返回匹配的文章列表")
    public List<ArticleInfo> searchArticles(
            @ToolParam(description = "搜索关键词") String keyword,
            @ToolParam(description = "返回数量限制，默认5") Integer limit,
            ToolContext toolContext) {
        log.info("搜索文章: keyword={}, limit={}", keyword, limit);
        try {
            if (limit == null || limit <= 0) {
                limit = 5;
            }
            List<ArticleInfo> articles = forumArticleService.searchArticles(keyword, limit);

            // 存入ToolResultHolder
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null && !articles.isEmpty()) {
                ToolResultHolder.put(requestId, "searchResult_articles", articles);
                log.info("文章搜索结果已存入ToolResultHolder: requestId={}, count={}", requestId, articles.size());
            }

            return articles;
        } catch (Exception e) {
            log.error("搜索文章失败: keyword={}", keyword, e);
            throw new RuntimeException("文章搜索失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询热门文章
     *
     * @param limit 返回数量限制
     * @param toolContext 工具上下文
     * @return 热门文章列表
     */
    @Tool(description = "查询热门文章列表，返回最新发布的文章")
    public List<ArticleInfo> queryHotArticles(
            @ToolParam(description = "返回数量限制，默认5") Integer limit,
            ToolContext toolContext) {
        log.info("查询热门文章: limit={}", limit);
        try {
            if (limit == null || limit <= 0) {
                limit = 5;
            }
            List<ArticleInfo> articles = forumArticleService.getArticleList(1, limit);

            // 存入ToolResultHolder
            String requestId = (String) toolContext.getContext().get("requestId");
            if (requestId != null && !articles.isEmpty()) {
                ToolResultHolder.put(requestId, "hotArticles", articles);
                log.info("热门文章已存入ToolResultHolder: requestId={}, count={}", requestId, articles.size());
            }

            return articles;
        } catch (Exception e) {
            log.error("查询热门文章失败", e);
            throw new RuntimeException("热门文章查询失败：" + e.getMessage(), e);
        }
    }
}
