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
 * 文件分片实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chunk_info")
public class ChunkInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileMd5;

    private Integer chunkIndex;

    private Integer chunkSize;

    private String chunkMd5;

    private String storagePath;

    private LocalDateTime createdAt;
}
