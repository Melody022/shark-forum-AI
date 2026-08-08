package com.itswy.paicodingai.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件上传记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_upload")
public class FileUpload {

    @TableId(type = IdType.INPUT)
    private String fileMd5;

    private String fileName;

    private Long totalSize;

    /**
     * 状态：0-上传中，1-合并中，2-已完成，3-解析中，4-解析完成，5-失败
     */
    private Integer status;

    private String userId;

    private Integer chunkSize;

    private Integer totalChunks;

    private String storagePath;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime mergedAt;

    private LocalDateTime parsedAt;

    // 状态常量
    public static final int STATUS_UPLOADING = 0;
    public static final int STATUS_MERGING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_PARSING = 3;
    public static final int STATUS_PARSED = 4;
    public static final int STATUS_FAILED = 5;
}
