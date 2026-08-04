package com.itswy.paicodingai.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库片段实体（存储在ES中）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {
    private String id;
    private Long documentId;
    private String title;
    private String chunkText;
    private Integer chunkIndex;
    private float[] embedding;
    private String category;
    private LocalDateTime createTime;
}
