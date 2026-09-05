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
 * 知识库文档分块实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Integer chunkIndex;

    private String content;

    /** PARENT 用于上下文恢复，CHILD 用于检索。 */
    private String chunkRole;

    /** 子块所属的父块 ID。父块自身为空。 */
    private Long parentId;

    private String parentKey;

    private String sourceBlockId;

    private String blockType;

    private Integer pageStart;

    private Integer pageEnd;

    private String sectionPath;

    /** 结构化来源信息，使用 JSON 字符串保存。 */
    private String metadataJson;

    private Integer searchable;

    private Integer tokenCount;

    private LocalDateTime createdAt;
}
