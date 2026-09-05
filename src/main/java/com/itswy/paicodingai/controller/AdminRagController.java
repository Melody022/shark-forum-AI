package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.knowledge.service.KnowledgeBaseService;
import com.itswy.paicodingai.knowledge.entity.KnowledgeBase;
import com.itswy.paicodingai.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * 本地 RAG 管理后台：模型切换、连接测试和知识库文件上传。
 * 生产环境应在网关或 Spring Security 层增加管理员鉴权。
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminRagController {

    private final ModelProviderService modelProviderService;
    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public String page(Model model) {
        return "admin-rag";
    }

    @GetMapping("/api/providers")
    @ResponseBody
    public Map<String, Object> providers() {
        return Map.of("success", true, "data", modelProviderService.getProviders());
    }

    @PutMapping("/api/providers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateProviders(
            @RequestBody ModelProviderService.UpdateRequest request) {
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", modelProviderService.updateLlmProviders(request, "local-admin")));
        } catch (Exception e) {
            log.warn("更新模型 Provider 失败", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/providers/test")
    @ResponseBody
    public Map<String, Object> testProvider(@RequestBody ModelProviderService.ProviderRequest request) {
        return Map.of("success", true, "data", modelProviderService.testConnection(request));
    }

    @GetMapping("/api/knowledge/bases")
    @ResponseBody
    public Map<String, Object> knowledgeBases(@RequestParam(defaultValue = "0") String userId) {
        return Map.of("success", true, "data", knowledgeBaseService.getAccessibleKnowledgeBases(userId));
    }

    @PostMapping("/api/knowledge/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") String userId,
            @RequestParam Long kbId) {
        try {
            if (file.isEmpty() || file.getOriginalFilename() == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "文件不能为空"));
            }
            String fileName = Path.of(file.getOriginalFilename()).getFileName().toString();
            String fileMd5 = md5(file.getBytes());
            Path uploadDir = Path.of("uploads", "merged", userId);
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(fileName);
            Files.write(target, file.getBytes());
            String fileType = extension(fileName);
            var document = knowledgeBaseService.uploadDocument(
                    kbId, fileMd5, fileName, fileType, file.getSize(), userId);
            return ResponseEntity.ok(Map.of("success", true, "data", document));
        } catch (Exception e) {
            log.warn("管理后台上传知识库文件失败", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private String md5(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "txt";
    }
}
