package com.itswy.paicodingai.file.service;

import com.itswy.paicodingai.file.entity.ChunkInfo;
import com.itswy.paicodingai.file.entity.FileUpload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文件上传服务接口
 */
public interface FileUploadService {

    /**
     * 初始化上传（检查文件是否已存在，返回分片策略）
     */
    Map<String, Object> initUpload(String fileMd5, String fileName, Long totalSize, String userId);

    /**
     * 上传分片
     */
    Map<String, Object> uploadChunk(String fileMd5, Integer chunkIndex, MultipartFile file,
                                     Long totalSize, String fileName, Integer totalChunks);

    /**
     * 查询上传状态
     */
    Map<String, Object> getUploadStatus(String fileMd5);

    /**
     * 合并文件
     */
    Map<String, Object> mergeChunks(String fileMd5, String fileName);

    /**
     * 获取文件信息
     */
    FileUpload getFileByMd5(String fileMd5);

    /**
     * 获取用户的所有文件
     */
    List<FileUpload> getUserFiles(String userId);

    /**
     * 删除文件
     */
    boolean deleteFile(String fileMd5, String userId);
}
