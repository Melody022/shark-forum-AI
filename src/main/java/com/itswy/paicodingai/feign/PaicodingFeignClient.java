package com.itswy.paicodingai.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * shark-forum微服务的Feign客户端
 *
 * 用于调用shark-forum主项目的API接口
 * 自动从Nacos获取服务地址，实现负载均衡
 */
@FeignClient(name = "shark-forum")
public interface PaicodingFeignClient {

    /**
     * 根据ID查询文章详情
     *
     * @param articleId 文章ID
     * @return 文章详情
     */
    @GetMapping("/article/api/data/detail/{articleId}")
    Object getArticleById(@PathVariable("articleId") Long articleId);

    /**
     * 查询文章列表
     *
     * @param page  页码
     * @param size  每页数量
     * @return 文章列表
     */
    @GetMapping("/article/api/list")
    Object getArticleList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size);

    /**
     * 搜索文章
     *
     * @param keyword 搜索关键词
     * @return 搜索结果
     */
    @GetMapping("/article/api/search")
    Object searchArticles(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit);

    /**
     * 查询标签列表
     *
     * @return 标签列表
     */
    @GetMapping("/article/api/tag/list")
    Object getTagList();
}
