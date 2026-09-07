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
 * 知识库表格元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_table")
public class KnowledgeTable {

    @TableId(type = IdType.INPUT)
    private String id;

    private String documentId;

    private String knowledgeBaseId;

    private String title;

    private Integer pageStart;

    private Integer pageEnd;

    private String sectionPath;

    /** 表格摘要（用于向量化） */
    private String summary;

    /** 表头JSON */
    private String headersJson;

    private Integer rowCount;

    private Integer columnCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
