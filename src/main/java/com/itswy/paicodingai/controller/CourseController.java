package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.service.forum.ForumColumnService;
import com.itswy.paicodingai.tools.result.CourseInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 教程详情页面
 *
 * 简易实现，用于卡片跳转
 */
@Controller
@RequiredArgsConstructor
public class CourseController {

    private final ForumColumnService forumColumnService;

    /**
     * 教程详情页
     */
    @GetMapping("/course/{id}")
    public String courseDetail(@PathVariable("id") Long id, Model model) {
        CourseInfo course = forumColumnService.getColumnById(id);
        if (course == null) {
            model.addAttribute("error", "教程不存在");
            return "error";
        }
        model.addAttribute("course", course);
        return "course-detail";
    }
}
