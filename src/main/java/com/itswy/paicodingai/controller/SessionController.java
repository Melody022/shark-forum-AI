package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.config.SessionProperties;
import com.itswy.paicodingai.service.ChatSessionService;
import com.itswy.paicodingai.vo.SessionVO;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/ai")
public class SessionController {

    private final ChatSessionService chatSessionService;

    public SessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    /**
     * 新建会话
     */
    @PostMapping("/session")
    public SessionVO createSession(@RequestParam(value = "n", defaultValue = "3") Integer num) {
        return this.chatSessionService.createSession(num);
    }

}
