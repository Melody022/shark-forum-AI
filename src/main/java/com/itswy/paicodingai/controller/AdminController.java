package com.itswy.paicodingai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 精简配置总控台(页面视图;数据走 /admin/api,由 AuthInterceptor 校验 ADMIN)。
 */
@Controller
public class AdminController {

    @GetMapping("/admin/console")
    public String console() {
        return "admin";
    }
}
