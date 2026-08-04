package com.itswy.paicodingai.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库文档实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {
    private Long id;
    private String title;
    private String content;
    private String category;
    private Integer chunkCount;
    private Integer status;  // 0-禁用，1-启用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
