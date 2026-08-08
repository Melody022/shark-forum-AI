package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.knowledge.entity.KnowledgeBase;
import com.itswy.paicodingai.knowledge.entity.KnowledgeChunk;
import com.itswy.paicodingai.knowledge.entity.KnowledgeDocument;
import com.itswy.paicodingai.knowledge.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建用户知识库
     * POST /api/v1/knowledge/base
     */
    @PostMapping("/base")
    public ResponseEntity<Map<String, Object>> createKnowledgeBase(
            @RequestBody Map<String, String> request) {

        String userId = request.get("userId");
        String name = request.get("name");
        String description = request.get("description");

        Map<String, Object> result = new HashMap<>();

        try {
            KnowledgeBase kb = knowledgeBaseService.createUserKnowledgeBase(userId, name, description);
            result.put("code", 200);
            result.put("message", "知识库创建成功");
            result.put("data", kb);
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取用户的所有知识库
     * GET /api/v1/knowledge/bases?userId=xxx
     */
    @GetMapping("/bases")
    public ResponseEntity<Map<String, Object>> getUserKnowledgeBases(@RequestParam String userId) {
        List<KnowledgeBase> bases = knowledgeBaseService.getAccessibleKnowledgeBases(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", bases);

        return ResponseEntity.ok(result);
    }

    /**
     * 上传文档到知识库
     * POST /api/v1/knowledge/document
     */
    @PostMapping("/document")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestBody Map<String, Object> request) {

        Long kbId = Long.valueOf(request.get("kbId").toString());
        String fileMd5 = (String) request.get("fileMd5");
        String fileName = (String) request.get("fileName");
        String fileType = (String) request.get("fileType");
        Long totalSize = Long.valueOf(request.get("totalSize").toString());
        String userId = (String) request.get("userId");

        Map<String, Object> result = new HashMap<>();

        try {
            KnowledgeDocument doc = knowledgeBaseService.uploadDocument(
                    kbId, fileMd5, fileName, fileType, totalSize, userId);
            result.put("code", 200);
            result.put("message", "文档上传成功，正在解析中");
            result.put("data", doc);
        } catch (Exception e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取知识库的所有文档
     * GET /api/v1/knowledge/documents?kbId=xxx&userId=xxx
     */
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> getDocuments(
            @RequestParam Long kbId,
            @RequestParam String userId) {

        Map<String, Object> result = new HashMap<>();

        try {
            List<KnowledgeDocument> docs = knowledgeBaseService.getKnowledgeBaseDocuments(kbId, userId);
            result.put("code", 200);
            result.put("data", docs);
        } catch (Exception e) {
            result.put("code", 403);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 搜索知识库
     * GET /api/v1/knowledge/search?query=xxx&userId=xxx&topK=5
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchKnowledgeBase(
            @RequestParam String query,
            @RequestParam String userId,
            @RequestParam(defaultValue = "5") int topK) {

        List<KnowledgeChunk> chunks = knowledgeBaseService.searchKnowledgeBase(query, userId, topK);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", chunks);

        return ResponseEntity.ok(result);
    }

    /**
     * 删除文档
     * DELETE /api/v1/knowledge/document/{docId}?userId=xxx
     */
    @DeleteMapping("/document/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable Long docId,
            @RequestParam String userId) {

        boolean success = knowledgeBaseService.deleteDocument(docId, userId);

        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "文档删除成功");
        } else {
            result.put("code", 404);
            result.put("message", "文档不存在或无权限删除");
        }

        return ResponseEntity.ok(result);
    }
}
