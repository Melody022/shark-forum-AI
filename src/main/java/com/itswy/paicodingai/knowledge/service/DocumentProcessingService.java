package com.itswy.paicodingai.knowledge.service;

import com.itswy.paicodingai.knowledge.entity.KnowledgeChunk;
import com.itswy.paicodingai.knowledge.entity.KnowledgeDocument;

import java.util.List;

/**
 * 文档处理服务接口
 */
public interface DocumentProcessingService {

    /**
     * 处理文档（解析 + 向量化）
     *
     * @param docId 文档ID
     */
    void processDocument(Long docId);

    /**
     * 批量处理文档
     *
     * @param docIds 文档ID列表
     */
    void processDocuments(List<Long> docIds);

    /**
     * 重新处理文档
     *
     * @param docId 文档ID
     */
    void reprocessDocument(Long docId);

    /**
     * 删除文档向量
     *
     * @param docId 文档ID
     */
    void deleteDocumentVectors(Long docId);
}
