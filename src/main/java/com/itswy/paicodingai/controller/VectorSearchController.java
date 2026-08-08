package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.knowledge.service.SearchResult;
import com.itswy.paicodingai.knowledge.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量搜索控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vector")
@RequiredArgsConstructor
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    /**
     * 语义搜索
     * GET /api/v1/vector/search?query=xxx&userId=xxx&topK=5
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam String userId,
            @RequestParam(defaultValue = "5") int topK) {

        Map<String, Object> result = new HashMap<>();

        try {
            List<SearchResult> searchResults = vectorSearchService.search(query, userId, topK);
            result.put("code", 200);
            result.put("data", searchResults);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 在指定知识库中搜索
     * GET /api/v1/vector/search/kb?query=xxx&kbId=xxx&topK=5
     */
    @GetMapping("/search/kb")
    public ResponseEntity<Map<String, Object>> searchInKnowledgeBase(
            @RequestParam String query,
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "5") int topK) {

        Map<String, Object> result = new HashMap<>();

        try {
            List<SearchResult> searchResults = vectorSearchService.searchInKnowledgeBase(query, kbId, topK);
            result.put("code", 200);
            result.put("data", searchResults);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
