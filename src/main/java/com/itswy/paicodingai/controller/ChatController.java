package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.dto.ChatDTO;
import com.itswy.paicodingai.service.ChatService;
import com.itswy.paicodingai.skilltree.ClassifyResult;
import com.itswy.paicodingai.skilltree.IntentClassifier;
import com.itswy.paicodingai.skilltree.SkillNode;
import com.itswy.paicodingai.skilltree.SkillRouter;
import com.itswy.paicodingai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天控制器 —— AI 对话相关接口
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final IntentClassifier intentClassifier;
    private final SkillRouter skillRouter;

    /**
     * 流式对话
     * POST /chat
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chat(@RequestBody ChatDTO chatDTO) {
        log.info("收到聊天请求：question={}, sessionId={}, userId={}",
                chatDTO.getQuestion(), chatDTO.getSessionId(), chatDTO.getUserId());
        return this.chatService.chat(chatDTO.getQuestion(), chatDTO.getSessionId(), chatDTO.getUserId());
    }

    /**
     * 停止生成
     * POST /chat/stop?sessionId=xxx
     */
    @PostMapping("/stop")
    public void stop(@RequestParam("sessionId") String sessionId) {
        this.chatService.stop(sessionId);
    }

    /**
     * 普通文本对话
     * POST /chat/text
     */
    @PostMapping("/text")
    public String chatText(@RequestBody String question) {
        return this.chatService.chatText(question);
    }

    /**
     * 意图分类测试（集成到AI助手接口）
     * GET /chat/intent?input=推荐热门文章
     */
    @GetMapping("/intent")
    public ResponseEntity<Map<String, Object>> classifyIntent(@RequestParam String input) {
        Map<String, Object> result = new HashMap<>();

        try {
            ClassifyResult classifyResult = intentClassifier.classify(input);
            SkillNode skillNode = skillRouter.route(input);

            result.put("code", 200);
            result.put("data", Map.of(
                "input", input,
                "intent", classifyResult.getIntent(),
                "confidence", classifyResult.getConfidence(),
                "method", classifyResult.getMethod(),
                "skillId", skillNode.getId(),
                "skillName", skillNode.getName()
            ));

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 批量意图分类测试
     * POST /chat/intent/test
     */
    @PostMapping("/intent/test")
    public ResponseEntity<Map<String, Object>> testIntentClassification(
            @RequestBody java.util.List<String> inputs) {

        Map<String, Object> result = new HashMap<>();
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

        for (String input : inputs) {
            try {
                ClassifyResult classifyResult = intentClassifier.classify(input);
                SkillNode skillNode = skillRouter.route(input);

                results.add(Map.of(
                    "input", input,
                    "intent", classifyResult.getIntent(),
                    "confidence", classifyResult.getConfidence(),
                    "method", classifyResult.getMethod(),
                    "skillId", skillNode.getId()
                ));

            } catch (Exception e) {
                results.add(Map.of(
                    "input", input,
                    "error", e.getMessage()
                ));
            }
        }

        result.put("code", 200);
        result.put("data", results);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取意图分类统计
     * GET /chat/intent/stats
     */
    @GetMapping("/intent/stats")
    public ResponseEntity<Map<String, Object>> getIntentStats() {
        Map<String, Object> result = new HashMap<>();

        try {
            SkillRouter.RouteStats stats = skillRouter.getStats();

            result.put("code", 200);
            result.put("data", stats);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
