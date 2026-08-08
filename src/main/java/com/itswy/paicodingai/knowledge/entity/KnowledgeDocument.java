package com.itswy.paicodingai.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String fileMd5;

    private String fileName;

    private String fileType;

    private Long totalSize;

    private Integer chunkCount;

    /**
     * 状态：0-解析中，1-已完成，2-失败
     */
    private Integer status;

    private String userId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime parsedAt;

    // 状态常量
    public static final int STATUS_PARSING = 0;
    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_FAILED = 2;
}
