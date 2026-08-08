package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.entity.KnowledgeBase;
import com.itswy.paicodingai.knowledge.entity.KnowledgeChunk;
import com.itswy.paicodingai.knowledge.entity.KnowledgeDocument;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeChunkMapper;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeDocumentMapper;
import com.itswy.paicodingai.knowledge.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档处理服务实现
 */
@Slf4j
@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private VectorizationService vectorizationService;

    @Autowired
    private VectorStoreService vectorStoreService;

    private static final String VECTOR_INDEX = "knowledge_vectors";

    @Override
    @Async
    public void processDocument(Long docId) {
        try {
            log.info("开始处理文档: docId={}", docId);

            // 获取文档
            KnowledgeDocument doc = documentMapper.selectById(docId);
            if (doc == null) {
                log.error("文档不存在: docId={}", docId);
                return;
            }

            // 获取所有分块
            List<KnowledgeChunk> chunks = chunkMapper.selectList(
                    new com.baomidou.mybatisplus.core.toolkit.Wrappers
                            .LambdaQueryWrapper<KnowledgeChunk>()
                            .eq(KnowledgeChunk::getDocId, docId)
                            .orderByAsc(KnowledgeChunk::getChunkIndex)
            );

            if (chunks.isEmpty()) {
                log.warn("文档没有分块: docId={}", docId);
                return;
            }

            log.info("文档分块数量: docId={}, chunks={}", docId, chunks.size());

            // 批量向量化
            List<String> texts = chunks.stream()
                    .map(KnowledgeChunk::getContent)
                    .toList();

            List<float[]> vectors = vectorizationService.embedBatch(texts);

            // 存储到Elasticsearch
            List<VectorData> vectorDataList = new java.util.ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                float[] vector = vectors.get(i);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("docId", docId);
                metadata.put("kbId", doc.getKbId());
                metadata.put("fileMd5", doc.getFileMd5());
                metadata.put("chunkIndex", chunk.getChunkIndex());
                metadata.put("content", chunk.getContent());
                metadata.put("userId", doc.getUserId());

                String vectorId = docId + "_" + chunk.getChunkIndex();
                vectorDataList.add(new VectorData(vectorId, vector, metadata));
            }

            vectorStoreService.storeBatch(VECTOR_INDEX, vectorDataList);

            log.info("文档处理完成: docId={}, 向量数={}", docId, vectorDataList.size());

        } catch (Exception e) {
            log.error("文档处理失败: docId={}", docId, e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void processDocuments(List<Long> docIds) {
        for (Long docId : docIds) {
            try {
                processDocument(docId);
            } catch (Exception e) {
                log.error("处理文档失败: docId={}", docId, e);
            }
        }
    }

    @Override
    public void reprocessDocument(Long docId) {
        // 先删除旧向量
        deleteDocumentVectors(docId);

        // 重新处理
        processDocument(docId);
    }

    @Override
    public void deleteDocumentVectors(Long docId) {
        try {
            vectorStoreService.deleteByField(VECTOR_INDEX, "docId", docId);
            log.info("删除文档向量成功: docId={}", docId);
        } catch (Exception e) {
            log.error("删除文档向量失败: docId={}", docId, e);
        }
    }
}
