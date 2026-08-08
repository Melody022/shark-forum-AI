package com.itswy.paicodingai.knowledge.service.impl;

import com.itswy.paicodingai.knowledge.entity.KnowledgeBase;
import com.itswy.paicodingai.knowledge.mapper.KnowledgeBaseMapper;
import com.itswy.paicodingai.knowledge.service.KnowledgeBaseService;
import com.itswy.paicodingai.knowledge.service.SearchResult;
import com.itswy.paicodingai.knowledge.service.VectorSearchService;
import com.itswy.paicodingai.knowledge.service.VectorizationService;
import com.itswy.paicodingai.knowledge.service.VectorSearchResult;
import com.itswy.paicodingai.knowledge.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量搜索服务实现
 */
@Slf4j
@Service
public class VectorSearchServiceImpl implements VectorSearchService {

    @Autowired
    private VectorizationService vectorizationService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    private static final String VECTOR_INDEX = "knowledge_vectors";

    @Override
    public List<SearchResult> search(String query, String userId, int topK) {
        try {
            // 获取用户可访问的知识库
            List<KnowledgeBase> accessibleKbs = knowledgeBaseService.getAccessibleKnowledgeBases(userId);

            if (accessibleKbs.isEmpty()) {
                log.warn("用户没有可访问的知识库: userId={}", userId);
                return List.of();
            }

            // 将查询文本向量化
            float[] queryVector = vectorizationService.embed(query);

            // 搜索向量
            List<VectorSearchResult> vectorResults = vectorStoreService.search(VECTOR_INDEX, queryVector, topK * 2);

            // 过滤出用户有权限访问的结果
            List<SearchResult> results = new ArrayList<>();
            List<Long> accessibleKbIds = accessibleKbs.stream()
                    .map(KnowledgeBase::getId)
                    .toList();

            for (VectorSearchResult vectorResult : vectorResults) {
                if (vectorResult.metadata.containsKey("kbId")) {
                    Long kbId = Long.valueOf(vectorResult.metadata.get("kbId").toString());
                    if (accessibleKbIds.contains(kbId)) {
                        results.add(new SearchResult(
                                (String) vectorResult.metadata.get("content"),
                                vectorResult.score,
                                Long.valueOf(vectorResult.metadata.get("docId").toString()),
                                kbId,
                                (String) vectorResult.metadata.get("fileMd5"),
                                Integer.valueOf(vectorResult.metadata.get("chunkIndex").toString())
                        ));
                    }
                }

                if (results.size() >= topK) {
                    break;
                }
            }

            log.info("向量搜索完成: query={}, userId={}, 结果数={}", query, userId, results.size());
            return results;

        } catch (Exception e) {
            log.error("向量搜索失败: query={}, userId={}", query, userId, e);
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SearchResult> searchInKnowledgeBase(String query, Long kbId, int topK) {
        try {
            // 将查询文本向量化
            float[] queryVector = vectorizationService.embed(query);

            // 搜索向量
            List<VectorSearchResult> vectorResults = vectorStoreService.search(VECTOR_INDEX, queryVector, topK);

            // 转换结果
            List<SearchResult> results = new ArrayList<>();
            for (VectorSearchResult vectorResult : vectorResults) {
                Long resultKbId = Long.valueOf(vectorResult.metadata.get("kbId").toString());
                if (resultKbId.equals(kbId)) {
                    results.add(new SearchResult(
                            (String) vectorResult.metadata.get("content"),
                            vectorResult.score,
                            Long.valueOf(vectorResult.metadata.get("docId").toString()),
                            kbId,
                            (String) vectorResult.metadata.get("fileMd5"),
                            Integer.valueOf(vectorResult.metadata.get("chunkIndex").toString())
                    ));
                }
            }

            log.info("知识库向量搜索完成: query={}, kbId={}, 结果数={}", query, kbId, results.size());
            return results;

        } catch (Exception e) {
            log.error("知识库向量搜索失败: query={}, kbId={}", query, kbId, e);
            throw new RuntimeException("知识库向量搜索失败: " + e.getMessage(), e);
        }
    }
}
