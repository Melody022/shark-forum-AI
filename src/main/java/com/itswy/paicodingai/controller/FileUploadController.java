package com.itswy.paicodingai.controller;

import com.itswy.paicodingai.file.entity.FileUpload;
import com.itswy.paicodingai.file.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 初始化上传
     * POST /api/v1/upload/init
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initUpload(
            @RequestParam String fileMd5,
            @RequestParam String fileName,
            @RequestParam Long totalSize,
            @RequestParam(defaultValue = "0") String userId) {

        Map<String, Object> result = fileUploadService.initUpload(fileMd5, fileName, totalSize, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * 上传分片
     * POST /api/v1/upload/chunk
     */
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam String fileMd5,
            @RequestParam Integer chunkIndex,
            @RequestParam MultipartFile file,
            @RequestParam Long totalSize,
            @RequestParam String fileName,
            @RequestParam(required = false) Integer totalChunks,
            @RequestParam(defaultValue = "0") String userId) {

        Map<String, Object> result = fileUploadService.uploadChunk(
                fileMd5, chunkIndex, file, totalSize, fileName, totalChunks);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询上传状态
     * GET /api/v1/upload/status?fileMd5=xxx
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@RequestParam String fileMd5) {
        Map<String, Object> result = fileUploadService.getUploadStatus(fileMd5);
        return ResponseEntity.ok(result);
    }

    /**
     * 合并文件
     * POST /api/v1/upload/merge
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeChunks(
            @RequestBody Map<String, String> request) {

        String fileMd5 = request.get("fileMd5");
        String fileName = request.get("fileName");

        Map<String, Object> result = fileUploadService.mergeChunks(fileMd5, fileName);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取文件信息
     * GET /api/v1/upload/file/{fileMd5}
     */
    @GetMapping("/file/{fileMd5}")
    public ResponseEntity<Map<String, Object>> getFileInfo(@PathVariable String fileMd5) {
        FileUpload fileUpload = fileUploadService.getFileByMd5(fileMd5);

        Map<String, Object> result = new HashMap<>();
        if (fileUpload != null) {
            result.put("code", 200);
            result.put("data", fileUpload);
        } else {
            result.put("code", 404);
            result.put("message", "文件不存在");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取用户的所有文件
     * GET /api/v1/upload/files?userId=xxx
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getUserFiles(@RequestParam String userId) {
        List<FileUpload> files = fileUploadService.getUserFiles(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", files);

        return ResponseEntity.ok(result);
    }

    /**
     * 删除文件
     * DELETE /api/v1/upload/file/{fileMd5}?userId=xxx
     */
    @DeleteMapping("/file/{fileMd5}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable String fileMd5,
            @RequestParam String userId) {

        boolean success = fileUploadService.deleteFile(fileMd5, userId);

        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "文件删除成功");
        } else {
            result.put("code", 404);
            result.put("message", "文件不存在或无权限删除");
        }

        return ResponseEntity.ok(result);
    }
}
