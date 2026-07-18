package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.vo.ChatReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
@Slf4j
public class ChatController {

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatReq request) throws IOException {
        SseEmitter emitter = new SseEmitter(0L);
        log.info("收到请求：{}", request);
        String reply = "你好！我是AI助手。你问的是：" + request.getQuestion();
        emitter.send(SseEmitter.event().data("{\"content\":\"" + reply + "\"}"));
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
        return emitter;
    }
}
