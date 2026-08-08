package com.itswy.paicodingai.file.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.itswy.paicodingai.file.entity.ChunkInfo;
import com.itswy.paicodingai.file.entity.FileUpload;
import com.itswy.paicodingai.file.mapper.ChunkInfoMapper;
import com.itswy.paicodingai.file.mapper.FileUploadMapper;
import com.itswy.paicodingai.file.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final FileUploadMapper fileUploadMapper;
    private final ChunkInfoMapper chunkInfoMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${file.upload.base-path:./uploads}")
    private String basePath;

    @Value("${file.upload.chunk-path:./uploads/chunks}")
    private String chunkPath;

    @Value("${file.upload merged-path:./uploads/merged}")
    private String mergedPath;

    private static final String UPLOAD_STATUS_KEY = "upload:status:";
    private static final long STATUS_EXPIRE_HOURS = 24;

    @Override
    public Map<String, Object> initUpload(String fileMd5, String fileName, Long totalSize, String userId) {
        Map<String, Object> result = new HashMap<>();

        // 检查文件是否已存在
        FileUpload existingFile = fileUploadMapper.selectById(fileMd5);
        if (existingFile != null && existingFile.getStatus() == FileUpload.STATUS_COMPLETED) {
            result.put("code", 200);
            result.put("message", "文件已存在");
            result.put("data", Map.of(
                    "fileMd5", fileMd5,
                    "exists", true,
                    "storagePath", existingFile.getStoragePath()
            ));
            return result;
        }

        // 计算分片策略
        int chunkSize = calculateChunkSize(totalSize);
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSize);

        // 创建或更新文件记录
        if (existingFile == null) {
            FileUpload fileUpload = FileUpload.builder()
                    .fileMd5(fileMd5)
                    .fileName(fileName)
                    .totalSize(totalSize)
                    .status(FileUpload.STATUS_UPLOADING)
                    .userId(userId)
                    .chunkSize(chunkSize)
                    .totalChunks(totalChunks)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            fileUploadMapper.insert(fileUpload);
        }

        // 获取已上传的分片
        List<Integer> uploadedChunks = getUploadedChunks(fileMd5);

        result.put("code", 200);
        result.put("message", "初始化成功");
        result.put("data", Map.of(
                "fileMd5", fileMd5,
                "chunkSize", chunkSize,
                "totalChunks", totalChunks,
                "uploaded", uploadedChunks
        ));

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> uploadChunk(String fileMd5, Integer chunkIndex, MultipartFile file,
                                            Long totalSize, String fileName, Integer totalChunks) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 验证文件记录是否存在
            FileUpload fileUpload = fileUploadMapper.selectById(fileMd5);
            if (fileUpload == null) {
                result.put("code", 404);
                result.put("message", "文件记录不存在，请先初始化上传");
                return result;
            }

            // 创建分片存储目录
            String chunkDir = chunkPath + "/" + fileMd5;
            Files.createDirectories(Paths.get(chunkDir));

            // 保存分片文件
            String chunkFileName = chunkIndex.toString();
            String chunkFilePath = chunkDir + "/" + chunkFileName;
            file.transferTo(new File(chunkFilePath));

            // 计算分片MD5
            String chunkMd5 = calculateFileMd5(new File(chunkFilePath));

            // 保存分片记录
            ChunkInfo chunkInfo = ChunkInfo.builder()
                    .fileMd5(fileMd5)
                    .chunkIndex(chunkIndex)
                    .chunkSize((int) file.getSize())
                    .chunkMd5(chunkMd5)
                    .storagePath(chunkFilePath)
                    .createdAt(LocalDateTime.now())
                    .build();

            // 删除旧的分片记录（如果存在）
            chunkInfoMapper.delete(Wrappers.<ChunkInfo>lambdaQuery()
                    .eq(ChunkInfo::getFileMd5, fileMd5)
                    .eq(ChunkInfo::getChunkIndex, chunkIndex));

            chunkInfoMapper.insert(chunkInfo);

            // 更新Redis状态
            String redisKey = UPLOAD_STATUS_KEY + fileMd5;
            redisTemplate.opsForHash().put(redisKey, chunkIndex.toString(), "1");
            redisTemplate.expire(redisKey, STATUS_EXPIRE_HOURS, TimeUnit.HOURS);

            // 获取已上传的分片列表
            List<Integer> uploadedChunks = getUploadedChunks(fileMd5);
            double progress = (double) uploadedChunks.size() / totalChunks * 100;

            result.put("code", 200);
            result.put("message", "分片上传成功");
            result.put("data", Map.of(
                    "uploaded", uploadedChunks,
                    "progress", Math.round(progress * 10.0) / 10.0
            ));

        } catch (Exception e) {
            log.error("分片上传失败: fileMd5={}, chunkIndex={}", fileMd5, chunkIndex, e);
            result.put("code", 500);
            result.put("message", "分片上传失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> getUploadStatus(String fileMd5) {
        Map<String, Object> result = new HashMap<>();

        FileUpload fileUpload = fileUploadMapper.selectById(fileMd5);
        if (fileUpload == null) {
            result.put("code", 404);
            result.put("message", "文件记录不存在");
            return result;
        }

        List<Integer> uploadedChunks = getUploadedChunks(fileMd5);
        double progress = fileUpload.getTotalChunks() != null ?
                (double) uploadedChunks.size() / fileUpload.getTotalChunks() * 100 : 0;

        result.put("code", 200);
        result.put("message", "Success");
        result.put("data", Map.of(
                "fileMd5", fileMd5,
                "fileName", fileUpload.getFileName(),
                "status", fileUpload.getStatus(),
                "uploaded", uploadedChunks,
                "progress", Math.round(progress * 10.0) / 10.0,
                "totalChunks", fileUpload.getTotalChunks() != null ? fileUpload.getTotalChunks() : 0
        ));

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> mergeChunks(String fileMd5, String fileName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取文件记录
            FileUpload fileUpload = fileUploadMapper.selectById(fileMd5);
            if (fileUpload == null) {
                result.put("code", 404);
                result.put("message", "文件记录不存在");
                return result;
            }

            // 检查所有分片是否已上传
            List<ChunkInfo> chunks = chunkInfoMapper.findByFileMd5(fileMd5);
            if (chunks.size() != fileUpload.getTotalChunks()) {
                result.put("code", 400);
                result.put("message", "不是所有分片都已上传");
                return result;
            }

            // 更新状态为合并中
            fileUpload.setStatus(FileUpload.STATUS_MERGING);
            fileUpload.setUpdatedAt(LocalDateTime.now());
            fileUploadMapper.updateById(fileUpload);

            // 创建合并文件目录
            String userMergedPath = mergedPath + "/" + fileUpload.getUserId();
            Files.createDirectories(Paths.get(userMergedPath));

            // 合并文件
            String mergedFilePath = userMergedPath + "/" + fileName;
            mergeFileChunks(fileMd5, chunks, mergedFilePath);

            // 更新文件记录
            fileUpload.setStatus(FileUpload.STATUS_COMPLETED);
            fileUpload.setStoragePath(mergedFilePath);
            fileUpload.setMergedAt(LocalDateTime.now());
            fileUpload.setUpdatedAt(LocalDateTime.now());
            fileUploadMapper.updateById(fileUpload);

            // 清理分片文件和记录
            cleanupChunks(fileMd5, chunks);

            // 清理Redis状态
            redisTemplate.delete(UPLOAD_STATUS_KEY + fileMd5);

            result.put("code", 200);
            result.put("message", "文件合并成功");
            result.put("data", Map.of(
                    "fileMd5", fileMd5,
                    "storagePath", mergedFilePath,
                    "fileSize", fileUpload.getTotalSize()
            ));

        } catch (Exception e) {
            log.error("文件合并失败: fileMd5={}", fileMd5, e);

            // 更新状态为失败
            FileUpload fileUpload = fileUploadMapper.selectById(fileMd5);
            if (fileUpload != null) {
                fileUpload.setStatus(FileUpload.STATUS_FAILED);
                fileUpload.setUpdatedAt(LocalDateTime.now());
                fileUploadMapper.updateById(fileUpload);
            }

            result.put("code", 500);
            result.put("message", "文件合并失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public FileUpload getFileByMd5(String fileMd5) {
        return fileUploadMapper.selectById(fileMd5);
    }

    @Override
    public List<FileUpload> getUserFiles(String userId) {
        return fileUploadMapper.selectList(
                Wrappers.<FileUpload>lambdaQuery()
                        .eq(FileUpload::getUserId, userId)
                        .orderByDesc(FileUpload::getCreatedAt)
        );
    }

    @Override
    @Transactional
    public boolean deleteFile(String fileMd5, String userId) {
        FileUpload fileUpload = fileUploadMapper.selectById(fileMd5);
        if (fileUpload == null) {
            return false;
        }

        // 检查权限
        if (!fileUpload.getUserId().equals(userId)) {
            return false;
        }

        // 删除分片记录
        chunkInfoMapper.delete(Wrappers.<ChunkInfo>lambdaQuery()
                .eq(ChunkInfo::getFileMd5, fileMd5));

        // 删除文件记录
        fileUploadMapper.deleteById(fileMd5);

        // 删除物理文件
        try {
            if (fileUpload.getStoragePath() != null) {
                Files.deleteIfExists(Paths.get(fileUpload.getStoragePath()));
            }
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", fileUpload.getStoragePath(), e);
        }

        return true;
    }

    // ========== 私有方法 ==========

    private int calculateChunkSize(Long totalSize) {
        // 默认分片大小：5MB
        int defaultChunkSize = 5 * 1024 * 1024;

        if (totalSize <= defaultChunkSize) {
            return totalSize.intValue();
        }

        // 根据文件大小动态调整分片大小
        int chunks = (int) Math.ceil((double) totalSize / defaultChunkSize);
        return (int) Math.ceil((double) totalSize / chunks);
    }

    private List<Integer> getUploadedChunks(String fileMd5) {
        List<ChunkInfo> chunks = chunkInfoMapper.findByFileMd5(fileMd5);
        return chunks.stream()
                .map(ChunkInfo::getChunkIndex)
                .sorted()
                .toList();
    }

    private void mergeFileChunks(String fileMd5, List<ChunkInfo> chunks, String mergedFilePath) throws IOException {
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(mergedFilePath))) {
            for (ChunkInfo chunk : chunks) {
                Path chunkPath = Paths.get(chunk.getStoragePath());
                if (Files.exists(chunkPath)) {
                    Files.copy(chunkPath, outputStream);
                }
            }
        }
    }

    private void cleanupChunks(String fileMd5, List<ChunkInfo> chunks) {
        for (ChunkInfo chunk : chunks) {
            try {
                Files.deleteIfExists(Paths.get(chunk.getStoragePath()));
            } catch (IOException e) {
                log.warn("删除分片文件失败: {}", chunk.getStoragePath(), e);
            }
        }

        // 删除分片目录
        try {
            Path chunkDir = Paths.get(chunkPath + "/" + fileMd5);
            if (Files.exists(chunkDir)) {
                Files.walk(chunkDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            log.warn("删除分片目录失败: {}", fileMd5, e);
        }
    }

    private String calculateFileMd5(File file) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            try (InputStream inputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算MD5失败", e);
        }
    }
}
