package com.itswy.paicodingai.service.forum;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itswy.paicodingai.entity.forum.Article;
import com.itswy.paicodingai.entity.forum.ArticleDetail;
import com.itswy.paicodingai.mapper.ArticleDetailMapper;
import com.itswy.paicodingai.mapper.ArticleMapper;
import com.itswy.paicodingai.tools.result.ArticleInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Forum 文章服务
 *
 * 直接查询 forum 数据库，不依赖微服务调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleDetailMapper articleDetailMapper;

    /**
     * 根据ID查询文章详情
     *
     * @param articleId 文章ID
     * @return 文章详情
     */
    public ArticleInfo getArticleById(Long articleId) {
        log.info("从数据库查询文章详情: articleId={}", articleId);

        // 查询文章基本信息
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            log.warn("文章不存在: articleId={}", articleId);
            return null;
        }

        // 查询文章内容
        ArticleDetail detail = articleDetailMapper.selectLatestByArticleId(articleId);

        // 转换为 ArticleInfo
        return ArticleInfo.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .content(detail != null ? detail.getContent() : null)
                .category(String.valueOf(article.getCategoryId()))
                .author(String.valueOf(article.getUserId()))
                .publishTime(article.getCreateTime() != null ? article.getCreateTime().toString() : null)
                .viewCount(0L)  // 需要关联 read_count 表查询
                .likeCount(0L)  // 需要关联 user_foot 表查询
                .tags("")  // 需要关联 article_tag 和 tag 表查询
                .build();
    }

    /**
     * 查询文章列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 文章列表
     */
    public List<ArticleInfo> getArticleList(int page, int size) {
        log.info("从数据库查询文章列表: page={}, size={}", page, size);

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, 1)  // 只查询已发布的
               .eq(Article::getDeleted, 0)  // 未删除的
               .orderByDesc(Article::getCreateTime)
               .last("LIMIT " + size + " OFFSET " + (page - 1) * size);

        List<Article> articles = articleMapper.selectList(wrapper);

        return articles.stream()
                .map(article -> ArticleInfo.builder()
                        .id(article.getId())
                        .title(article.getTitle())
                        .summary(article.getSummary())
                        .category(String.valueOf(article.getCategoryId()))
                        .author(String.valueOf(article.getUserId()))
                        .publishTime(article.getCreateTime() != null ? article.getCreateTime().toString() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 根据关键词搜索文章
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量限制
     * @return 文章列表
     */
    public List<ArticleInfo> searchArticles(String keyword, int limit) {
        log.info("从数据库搜索文章: keyword={}, limit={}", keyword, limit);

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, 1)
               .eq(Article::getDeleted, 0)
               .and(w -> w.like(Article::getTitle, keyword)
                         .or()
                         .like(Article::getSummary, keyword))
               .orderByDesc(Article::getCreateTime)
               .last("LIMIT " + limit);

        List<Article> articles = articleMapper.selectList(wrapper);

        return articles.stream()
                .map(article -> ArticleInfo.builder()
                        .id(article.getId())
                        .title(article.getTitle())
                        .summary(article.getSummary())
                        .category(String.valueOf(article.getCategoryId()))
                        .author(String.valueOf(article.getUserId()))
                        .publishTime(article.getCreateTime() != null ? article.getCreateTime().toString() : null)
                        .build())
                .collect(Collectors.toList());
    }
}
