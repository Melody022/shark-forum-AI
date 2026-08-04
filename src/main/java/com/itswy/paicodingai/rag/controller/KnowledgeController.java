package com.itswy.paicodingai.rag.controller;

import com.itswy.paicodingai.rag.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理控制器
 *
 * 提供文档的增删改查功能
 */
@Slf4j
@Controller
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 知识库管理页面
     */
    @GetMapping
    public String knowledgePage(Model model) {
        return "knowledge";
    }

    /**
     * 获取所有文档
     */
    @GetMapping("/list")
    @ResponseBody
    public Map<String, Object> getDocuments() {
        try {
            List<Document> documents = knowledgeService.getAllDocuments();
            return Map.of(
                "success", true,
                "data", documents.stream().map(doc -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", doc.getId());
                    item.put("text", doc.getText());
                    item.put("metadata", doc.getMetadata());
                    return item;
                }).toList()
            );
        } catch (Exception e) {
            log.error("获取文档列表失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 搜索文档
     */
    @PostMapping("/search")
    @ResponseBody
    public Map<String, Object> searchDocuments(@RequestParam("query") String query,
                                               @RequestParam(value = "topK", defaultValue = "5") int topK) {
        try {
            List<Document> results = knowledgeService.search(query, topK);
            return Map.of(
                "success", true,
                "data", results.stream().map(doc -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", doc.getId());
                    item.put("text", doc.getText());
                    item.put("metadata", doc.getMetadata());
                    return item;
                }).toList()
            );
        } catch (Exception e) {
            log.error("搜索文档失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 添加文档
     */
    @PostMapping("/add")
    @ResponseBody
    public Map<String, Object> addDocument(@RequestParam("title") String title,
                                           @RequestParam("content") String content,
                                           @RequestParam(value = "category", defaultValue = "通用") String category) {
        try {
            List<String> chunkIds = knowledgeService.addDocument(title, content, category);
            return Map.of(
                "success", true,
                "message", "文档添加成功",
                "chunkCount", chunkIds.size()
            );
        } catch (Exception e) {
            log.error("添加文档失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/detail/{id}")
    @ResponseBody
    public Map<String, Object> getDocumentDetail(@PathVariable("id") String id) {
        try {
            KnowledgeService.DocumentInfo info = knowledgeService.getDocumentDetail(id);
            if (info != null) {
                return Map.of(
                    "success", true,
                    "data", Map.of(
                        "id", info.id(),
                        "title", info.title(),
                        "content", info.content(),
                        "category", info.category(),
                        "chunkCount", info.chunkCount()
                    )
                );
            } else {
                return Map.of("success", false, "message", "文档不存在");
            }
        } catch (Exception e) {
            log.error("获取文档详情失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 更新文档
     */
    @PostMapping("/update/{id}")
    @ResponseBody
    public Map<String, Object> updateDocument(@PathVariable("id") String id,
                                              @RequestParam("title") String title,
                                              @RequestParam("content") String content,
                                              @RequestParam(value = "category", defaultValue = "通用") String category) {
        try {
            knowledgeService.updateDocument(id, title, content, category);
            return Map.of("success", true, "message", "文档更新成功");
        } catch (Exception e) {
            log.error("更新文档失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/delete/{id}")
    @ResponseBody
    public Map<String, Object> deleteDocument(@PathVariable("id") String id) {
        try {
            knowledgeService.deleteDocument(id);
            return Map.of("success", true, "message", "文档删除成功");
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
