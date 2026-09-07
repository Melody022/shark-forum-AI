package com.itswy.paicodingai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 门户(登录)首页:未登录统一入口,登录后按角色展示 用户/管理 入口。
 */
@Controller
public class PortalController {

    @GetMapping("/")
    public String portal() {
        return "portal";
    }
}
