package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.service.forum.ForumArticleService;
import com.itswy.paicodingai.tools.result.ArticleInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 文章详情页面
 *
 * 简易实现，用于卡片跳转
 */
@Controller
@RequiredArgsConstructor
public class ArticleController {

    private final ForumArticleService forumArticleService;

    /**
     * 文章详情页
     */
    @GetMapping("/article/{id}")
    public String articleDetail(@PathVariable("id") Long id, Model model) {
        ArticleInfo article = forumArticleService.getArticleById(id);
        if (article == null) {
            model.addAttribute("error", "文章不存在");
            return "error";
        }
        model.addAttribute("article", article);
        return "article-detail";
    }
}
