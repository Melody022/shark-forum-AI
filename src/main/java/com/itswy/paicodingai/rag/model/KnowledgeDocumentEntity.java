package com.itswy.paicodingai.rag.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档实体（MyBatis-Plus）
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String category;

    private Integer chunkCount;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
